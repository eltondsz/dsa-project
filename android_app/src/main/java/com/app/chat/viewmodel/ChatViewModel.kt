package com.app.chat.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.chat.ble.BleMeshManager
import com.app.chat.bridge.PythonCoreBridge
import com.app.chat.model.ChatConversation
import com.app.chat.model.MediaType
import com.app.chat.model.MessageItem
import com.app.chat.model.MessageStatus
import com.app.chat.model.NavigationTab
import com.app.chat.model.PeerDevice
import com.app.chat.model.Screen
import com.app.chat.model.StorageStats
import com.app.chat.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatViewModel : ViewModel() {

    var currentScreen by mutableStateOf(Screen.Splash)
        private set

    private val screenStack = mutableListOf<Screen>()

    var activeTab by mutableStateOf(NavigationTab.Chats)
        private set

    var activeConversationId by mutableStateOf<String?>(null)
        private set

    var searchQuery by mutableStateOf("")

    var userProfile by mutableStateOf(
        UserProfile(
            id = "",
            displayName = android.os.Build.MODEL.ifBlank { "Offline Node" },
            publicKey = "",
            isDiscoveryEnabled = true,
            bio = "Encrypted BLE Mesh Node • Offline First"
        )
    )
        private set

    var storageStats by mutableStateOf(StorageStats())
        private set

    var isScanningNearby by mutableStateOf(true)
    var isRecordingVoice by mutableStateOf(false)
    var voiceRecordDurationSeconds by mutableStateOf(0)
    var isPlayingVoiceId by mutableStateOf<String?>(null)
    var showQrDialog by mutableStateOf(false)
    var showPanicDialog by mutableStateOf(false)
    var showAttachmentDialog by mutableStateOf(false)
    var isBlePermissionGranted by mutableStateOf(false)

    val mnemonicWords = listOf(
        "anchor", "cipher", "beacon", "mesh", "signal", "quantum",
        "forest", "shield", "pulse", "matrix", "vector", "frost"
    )

    val conversations = mutableStateListOf<ChatConversation>()
    val messagesMap = mutableStateMapOf<String, MutableList<MessageItem>>()
    val nearbyPeers = mutableStateListOf<PeerDevice>()
    val selectedGroupPeerIds = mutableStateListOf<String>()
    var newGroupName by mutableStateOf("")

    var bleMeshManager: BleMeshManager? = null
        private set

    var isBluetoothEnabled by mutableStateOf(false)
    var onPromptEnableBluetooth: (() -> Unit)? = null

    fun checkBluetoothStatus() {
        isBluetoothEnabled = bleMeshManager?.isBluetoothEnabled() == true
    }

    fun requestEnableBluetooth() {
        if (bleMeshManager?.enableBluetoothDirectly() == true) {
            isBluetoothEnabled = true
            bleMeshManager?.initialize()
            bleMeshManager?.startAdvertising(userProfile.displayName)
            bleMeshManager?.startScanning()
        } else {
            onPromptEnableBluetooth?.invoke()
        }
    }

    fun setBleManager(manager: BleMeshManager) {
        this.bleMeshManager = manager
        checkBluetoothStatus()
        manager.onPeerDiscovered = { peer ->
            viewModelScope.launch {
                val existing = nearbyPeers.find { it.id == peer.id || (it.bleAddress.isNotBlank() && it.bleAddress == peer.id) }
                if (existing == null) {
                    nearbyPeers.add(peer)
                    val handshakeHex = PythonCoreBridge.prepareHandshakePacket()
                    if (handshakeHex != null) {
                        try {
                            manager.sendPayloadToPeer(peer.id, hexStringToByteArray(handshakeHex))
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "Error sending handshake: ${e.message}")
                        }
                    }
                } else {
                    val idx = nearbyPeers.indexOf(existing)
                    nearbyPeers[idx] = existing.copy(
                        rssi = peer.rssi,
                        distanceText = peer.distanceText,
                        distanceMeters = peer.distanceMeters,
                        isOnline = true
                    )
                }
            }
        }

        manager.onPacketReceived = { packet, senderAddress ->
            handleIncomingPacket(packet, senderAddress)
        }
    }

    fun toggleScanning() {
        isScanningNearby = !isScanningNearby
        if (isScanningNearby) {
            bleMeshManager?.startScanning()
        } else {
            bleMeshManager?.stopScanning()
        }
    }

    private fun handleIncomingPacket(packet: ByteArray, senderAddress: String?) {
        try {
            val result = PythonCoreBridge.processIncomingBle(packet) ?: return
            val code = (result["code"] as? Number)?.toInt() ?: -1
            if (code != 0) {
                Log.d("ChatViewModel", "Packet processed with code: $code")
                return
            }

            val packetType = (result["packet_type"] as? Number)?.toInt()
            val senderId = result["sender_id"]?.toString() ?: ""

            // Register MAC <-> UUID mapping
            if (senderAddress != null && senderId.isNotBlank()) {
                bleMeshManager?.registerPeerAddressMapping(senderId, senderAddress)
                PythonCoreBridge.associatePeerAddress(senderId, senderAddress)
            }

            if (packetType == 1) { // MESSAGE
                val msgId = result["msg_id"]?.toString() ?: UUID.randomUUID().toString()
                val messageText = result["message"]?.toString() ?: ""
                val timestamp = getCurrentTime()

                viewModelScope.launch {
                    val existingConv = conversations.find {
                        it.id == "chat-$senderId" ||
                            it.peerUuid == senderId ||
                            (senderAddress != null && (it.id == "chat-$senderAddress" || it.peerAddress == senderAddress))
                    }

                    val targetConvId = existingConv?.id ?: "chat-$senderId"
                    val peerName = existingConv?.name
                        ?: nearbyPeers.find { it.id == senderId || it.peerUuid == senderId || it.bleAddress == senderAddress }?.name
                        ?: "Node ${senderId.take(6)}"

                    if (existingConv == null) {
                        val newConv = ChatConversation(
                            id = targetConvId,
                            name = peerName,
                            lastMessage = messageText,
                            lastTimestamp = "Just now",
                            unreadCount = if (activeConversationId == targetConvId) 0 else 1,
                            isGroup = false,
                            avatarInitials = peerName.take(1).uppercase(),
                            isOnline = true,
                            peerAddress = senderAddress ?: "",
                            peerUuid = senderId
                        )
                        conversations.add(0, newConv)
                    } else {
                        val idx = conversations.indexOf(existingConv)
                        conversations.removeAt(idx)
                        conversations.add(0, existingConv.copy(
                            lastMessage = messageText,
                            lastTimestamp = "Just now",
                            unreadCount = if (activeConversationId == targetConvId) 0 else existingConv.unreadCount + 1,
                            peerAddress = if (existingConv.peerAddress.isBlank() && senderAddress != null) senderAddress else existingConv.peerAddress,
                            peerUuid = if (existingConv.peerUuid.isBlank()) senderId else existingConv.peerUuid,
                            isOnline = true
                        ))
                    }

                    val list = messagesMap.getOrPut(targetConvId) { mutableStateListOf() }
                    if (!list.any { it.id == msgId }) {
                        list.add(
                            MessageItem(
                                id = msgId,
                                conversationId = targetConvId,
                                senderId = senderId,
                                senderName = peerName,
                                text = messageText,
                                timestamp = timestamp,
                                isIncoming = true,
                                status = MessageStatus.Delivered
                            )
                        )
                    }
                }
            } else {
                // Handshake packet: send mutual handshake response back so peer has our keys
                if (senderAddress != null) {
                    val myHandshake = PythonCoreBridge.prepareHandshakePacket()
                    if (myHandshake != null) {
                        try {
                            bleMeshManager?.sendPayloadToPeer(senderAddress, hexStringToByteArray(myHandshake))
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "Error sending mutual handshake: ${e.message}")
                        }
                    }
                }

                viewModelScope.launch {
                    val idx = nearbyPeers.indexOfFirst {
                        it.id == senderAddress || it.bleAddress == senderAddress || it.id == senderId || it.peerUuid == senderId
                    }
                    if (idx >= 0) {
                        val oldPeer = nearbyPeers[idx]
                        nearbyPeers[idx] = oldPeer.copy(
                            peerUuid = senderId,
                            bleAddress = senderAddress ?: oldPeer.bleAddress,
                            isOnline = true
                        )
                    } else if (senderId.isNotBlank()) {
                        val name = "Node ${senderId.take(6)}"
                        nearbyPeers.add(
                            PeerDevice(
                                id = senderAddress ?: senderId,
                                name = name,
                                distanceText = "~ 3 m",
                                distanceMeters = 3f,
                                rssi = -60,
                                isOnline = true,
                                bleAddress = senderAddress ?: "",
                                peerUuid = senderId
                            )
                        )
                    }

                    val convIdx = conversations.indexOfFirst {
                        it.peerAddress == senderAddress || it.id == "chat-$senderAddress" || it.id == "chat-$senderId"
                    }
                    if (convIdx >= 0) {
                        val conv = conversations[convIdx]
                        conversations[convIdx] = conv.copy(
                            peerUuid = senderId,
                            peerAddress = senderAddress ?: conv.peerAddress,
                            isOnline = true
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error in handleIncomingPacket: ${e.message}")
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.trim()
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) + Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        val deviceId = PythonCoreBridge.getDeviceId()
        val pubKey = PythonCoreBridge.getPublicKey()
        val defaultName = android.os.Build.MODEL.ifBlank { "Node ${deviceId.take(4)}" }
        userProfile = userProfile.copy(
            id = deviceId,
            displayName = if (userProfile.displayName.isBlank() || userProfile.displayName == "Offline Node") defaultName else userProfile.displayName,
            publicKey = pubKey
        )

        // Real storage metrics from SQLite
        val storageBreakdown = PythonCoreBridge.getStorageBreakdown()
        val msgMb = storageBreakdown["messages"] ?: 0L
        val imgMb = storageBreakdown["images"] ?: 0L
        val vMb = storageBreakdown["voice"] ?: 0L
        val oMb = storageBreakdown["other"] ?: 1L
        val msgBytes = msgMb * 1_000_000L
        val imgBytes = imgMb * 1_000_000L
        val vBytes = vMb * 1_000_000L
        val oBytes = oMb * 1_000_000L
        storageStats = StorageStats(
            usedBytes = msgBytes + imgBytes + vBytes + oBytes,
            messagesBytes = msgBytes,
            imagesBytes = imgBytes,
            voiceBytes = vBytes,
            otherBytes = oBytes,
            totalBytes = 100_000_000L
        )

        conversations.clear()
        messagesMap.clear()
        nearbyPeers.clear()
        selectedGroupPeerIds.clear()

        // Load real peers from Python SQLite database
        val backendPeers = PythonCoreBridge.getPeers()
        backendPeers.forEach { p ->
            val id = p["id"]?.toString() ?: return@forEach
            val name = p["name"]?.toString() ?: "Mesh Peer ${id.take(6)}"
            val dist = p["distance"]?.toString() ?: "~ 5 m"
            val rssi = (p["rssi"] as? Number)?.toInt() ?: -60
            val peer = PeerDevice(id = id, name = name, distanceText = dist, distanceMeters = 5f, rssi = rssi)
            if (!nearbyPeers.any { it.id == id }) {
                nearbyPeers.add(peer)
            }

            // Load real decrypted message history from database
            val peerMessages = PythonCoreBridge.getMessagesForPeer(id)
            if (peerMessages.isNotEmpty()) {
                val list = mutableStateListOf<MessageItem>()
                peerMessages.forEach { m ->
                    val mId = m["msg_id"]?.toString() ?: UUID.randomUUID().toString()
                    val senderId = m["sender_id"]?.toString() ?: id
                    val isIncoming = senderId != userProfile.id
                    val text = m["message"]?.toString() ?: ""
                    val ts = formatTimestamp(m["timestamp"])
                    val status = if (isIncoming) MessageStatus.Delivered else MessageStatus.Sent
                    list.add(
                        MessageItem(
                            id = mId,
                            conversationId = "chat-$id",
                            senderId = senderId,
                            senderName = if (isIncoming) name else userProfile.displayName,
                            text = text,
                            timestamp = ts,
                            isIncoming = isIncoming,
                            status = status
                        )
                    )
                }
                messagesMap["chat-$id"] = list
                val lastMsg = list.last()
                conversations.add(
                    ChatConversation(
                        id = "chat-$id",
                        name = name,
                        lastMessage = lastMsg.text,
                        lastTimestamp = lastMsg.timestamp,
                        unreadCount = 0,
                        isGroup = false,
                        avatarInitials = name.take(1).uppercase(),
                        isOnline = true
                    )
                )
            }
        }
    }

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            screenStack.add(currentScreen)
            currentScreen = screen
        }
    }

    fun handleBack(): Boolean {
        if (showQrDialog) {
            showQrDialog = false
            return true
        }
        if (showPanicDialog) {
            showPanicDialog = false
            return true
        }
        if (showAttachmentDialog) {
            showAttachmentDialog = false
            return true
        }

        if (screenStack.isNotEmpty()) {
            val previous = screenStack.removeAt(screenStack.size - 1)
            currentScreen = previous
            if (previous == Screen.Home) {
                activeConversationId = null
                activeTab = NavigationTab.Chats
            }
            return true
        }

        if (currentScreen != Screen.Home && currentScreen != Screen.Splash) {
            currentScreen = Screen.Home
            activeConversationId = null
            activeTab = NavigationTab.Chats
            return true
        }

        return false
    }

    fun selectTab(tab: NavigationTab) {
        activeTab = tab
        when (tab) {
            NavigationTab.Chats -> navigateTo(Screen.Home)
            NavigationTab.Nearby -> navigateTo(Screen.Nearby)
            NavigationTab.Groups -> navigateTo(Screen.CreateGroup)
            NavigationTab.Settings -> navigateTo(Screen.Settings)
        }
    }

    fun openConversation(conversationId: String) {
        activeConversationId = conversationId
        val conv = conversations.find { it.id == conversationId }
        if (conv != null && conv.unreadCount > 0) {
            val index = conversations.indexOf(conv)
            conversations[index] = conv.copy(unreadCount = 0)
        }
        if (conv?.isGroup == true) {
            navigateTo(Screen.GroupChat)
        } else {
            navigateTo(Screen.DirectChat)
        }
    }

    fun openDirectChatWithPeer(peer: PeerDevice) {
        val peerAddress = peer.bleAddress.ifBlank { peer.id }
        val peerUuid = peer.peerUuid.ifBlank { bleMeshManager?.getUuidForAddress(peerAddress) ?: "" }

        val existingConv = conversations.find {
            it.id == "chat-$peerAddress" ||
                (peerUuid.isNotBlank() && (it.id == "chat-$peerUuid" || it.peerUuid == peerUuid)) ||
                it.peerAddress == peerAddress ||
                it.name == peer.name
        }

        if (existingConv != null) {
            openConversation(existingConv.id)
        } else {
            val convId = if (peerUuid.isNotBlank()) "chat-$peerUuid" else "chat-$peerAddress"
            val newConv = ChatConversation(
                id = convId,
                name = peer.name,
                lastMessage = "Direct mesh connection established",
                lastTimestamp = "Just now",
                unreadCount = 0,
                isGroup = false,
                avatarInitials = peer.name.take(1).uppercase(),
                isOnline = true,
                peerAddress = peerAddress,
                peerUuid = peerUuid
            )
            conversations.add(0, newConv)
            messagesMap[newConv.id] = mutableStateListOf()
            openConversation(newConv.id)
        }

        // Transmit handshake packet over BLE to initiate/refresh cryptographic key exchange
        val handshakeHex = PythonCoreBridge.prepareHandshakePacket()
        if (handshakeHex != null) {
            try {
                bleMeshManager?.sendPayloadToPeer(peerAddress, hexStringToByteArray(handshakeHex))
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending handshake to peer $peerAddress: ${e.message}")
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || activeConversationId == null) return
        val convId = activeConversationId!!
        val timestamp = getCurrentTime()
        val msgId = UUID.randomUUID().toString()

        val conv = conversations.find { it.id == convId }
        val targetUuid = conv?.peerUuid?.ifBlank { null }
            ?: bleMeshManager?.getUuidForAddress(conv?.peerAddress ?: "")
            ?: convId.removePrefix("chat-")
        val targetMac = conv?.peerAddress?.ifBlank { null }
            ?: bleMeshManager?.getAddressForUuid(targetUuid)
            ?: targetUuid

        if (targetUuid.isNotBlank() && targetMac.isNotBlank()) {
            PythonCoreBridge.associatePeerAddress(targetUuid, targetMac)
        }

        val packetHex = PythonCoreBridge.prepareOutgoingMessage(targetUuid, text)
        if (packetHex != null) {
            try {
                val packetBytes = hexStringToByteArray(packetHex)
                bleMeshManager?.sendPayloadToPeer(targetMac, packetBytes)
                bleMeshManager?.broadcastPacket(packetBytes)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending packet: ${e.message}")
            }
        } else {
            Log.w("ChatViewModel", "Failed to prepare outgoing message for target '$targetUuid'")
        }

        val sendingMessage = MessageItem(
            id = msgId,
            conversationId = convId,
            senderId = userProfile.id,
            senderName = userProfile.displayName,
            text = text,
            timestamp = timestamp,
            isIncoming = false,
            status = if (packetHex != null) MessageStatus.Sent else MessageStatus.Pending,
            mediaType = MediaType.Text
        )

        val list = messagesMap.getOrPut(convId) { mutableStateListOf() }
        list.add(sendingMessage)

        val convIndex = conversations.indexOfFirst { it.id == convId }
        if (convIndex >= 0) {
            val oldConv = conversations[convIndex]
            conversations.removeAt(convIndex)
            conversations.add(0, oldConv.copy(lastMessage = text, lastTimestamp = "Just now"))
        }

        viewModelScope.launch {
            delay(150)
            val idx = list.indexOfFirst { it.id == msgId }
            if (idx >= 0 && packetHex != null) {
                list[idx] = list[idx].copy(status = MessageStatus.Delivered)
            }
        }
    }

    private fun formatTimestamp(rawTimestamp: Any?): String {
        return try {
            val epochSec = (rawTimestamp as? Number)?.toLong() ?: return "Earlier"
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            sdf.format(Date(epochSec * 1000L))
        } catch (_: Exception) {
            "Earlier"
        }
    }

    fun sendImageMessage(caption: String = "Shared photo from camera") {
        val convId = activeConversationId ?: return
        val timestamp = getCurrentTime()
        val msgId = UUID.randomUUID().toString()

        val imageMsg = MessageItem(
            id = msgId,
            conversationId = convId,
            senderId = userProfile.id,
            senderName = userProfile.displayName,
            text = caption,
            timestamp = timestamp,
            isIncoming = false,
            status = MessageStatus.Delivered,
            mediaType = MediaType.Image,
            mediaThumbnail = "photo_sample"
        )
        messagesMap.getOrPut(convId) { mutableStateListOf() }.add(imageMsg)

        val convIndex = conversations.indexOfFirst { it.id == convId }
        if (convIndex >= 0) {
            val oldConv = conversations[convIndex]
            conversations.removeAt(convIndex)
            conversations.add(0, oldConv.copy(lastMessage = "📷 Image: $caption", lastTimestamp = "Just now"))
        }
        showAttachmentDialog = false
    }

    fun toggleVoiceRecording() {
        if (isRecordingVoice) {
            // Stop and send voice message
            isRecordingVoice = false
            val durationStr = String.format(Locale.getDefault(), "0:%02d", maxOf(1, voiceRecordDurationSeconds))
            voiceRecordDurationSeconds = 0

            val convId = activeConversationId ?: return
            val voiceMsg = MessageItem(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                senderId = userProfile.id,
                senderName = userProfile.displayName,
                text = "Voice message ($durationStr)",
                timestamp = getCurrentTime(),
                isIncoming = false,
                status = MessageStatus.Delivered,
                mediaType = MediaType.Voice,
                mediaDuration = durationStr
            )
            messagesMap.getOrPut(convId) { mutableStateListOf() }.add(voiceMsg)

            val convIndex = conversations.indexOfFirst { it.id == convId }
            if (convIndex >= 0) {
                val oldConv = conversations[convIndex]
                conversations.removeAt(convIndex)
                conversations.add(0, oldConv.copy(lastMessage = "🎙 Voice message ($durationStr)", lastTimestamp = "Just now"))
            }
        } else {
            isRecordingVoice = true
            voiceRecordDurationSeconds = 1
            viewModelScope.launch {
                while (isRecordingVoice) {
                    delay(1000)
                    if (isRecordingVoice) voiceRecordDurationSeconds++
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        isRecordingVoice = false
        voiceRecordDurationSeconds = 0
    }

    fun togglePeerSelection(peerId: String) {
        if (selectedGroupPeerIds.contains(peerId)) {
            selectedGroupPeerIds.remove(peerId)
        } else {
            selectedGroupPeerIds.add(peerId)
        }
    }

    fun createGroup() {
        val groupName = if (newGroupName.isBlank()) "Mesh Squad" else newGroupName
        val groupId = "group-${UUID.randomUUID()}"
        val memberCount = selectedGroupPeerIds.size + 1

        val newGroup = ChatConversation(
            id = groupId,
            name = groupName,
            lastMessage = "Group created with $memberCount members",
            lastTimestamp = "Just now",
            unreadCount = 0,
            isGroup = true,
            avatarInitials = groupName.take(1).uppercase(),
            isOnline = true,
            membersCount = memberCount
        )

        conversations.add(0, newGroup)
        messagesMap[groupId] = mutableStateListOf(
            MessageItem(
                id = UUID.randomUUID().toString(),
                conversationId = groupId,
                senderId = userProfile.id,
                senderName = userProfile.displayName,
                text = "Group created. All packets in this channel are end-to-end encrypted.",
                timestamp = getCurrentTime(),
                isIncoming = false,
                status = MessageStatus.Delivered
            )
        )

        newGroupName = ""
        selectedGroupPeerIds.clear()

        openConversation(groupId)
    }

    fun toggleVoicePlay(messageId: String) {
        isPlayingVoiceId = if (isPlayingVoiceId == messageId) null else messageId
    }

    fun updateProfile(displayName: String, isDiscoveryEnabled: Boolean, bio: String) {
        userProfile = userProfile.copy(
            displayName = displayName,
            isDiscoveryEnabled = isDiscoveryEnabled,
            bio = bio
        )
        if (isDiscoveryEnabled && isBluetoothEnabled) {
            bleMeshManager?.startAdvertising(displayName)
        } else if (!isDiscoveryEnabled) {
            bleMeshManager?.stopAdvertising()
        }
    }

    fun clearCache() {
        val storageBreakdown = PythonCoreBridge.getStorageBreakdown()
        val msgMb = storageBreakdown["messages"] ?: 0L
        val msgBytes = msgMb * 1_000_000L
        storageStats = storageStats.copy(
            usedBytes = msgBytes + 1024L,
            messagesBytes = msgBytes,
            imagesBytes = 0L,
            voiceBytes = 0L,
            otherBytes = 1024L
        )
    }

    fun triggerPanicWipe() {
        PythonCoreBridge.triggerPanicWipe()
        conversations.clear()
        messagesMap.clear()
        nearbyPeers.clear()
        val newDeviceId = PythonCoreBridge.getDeviceId()
        val newPubKey = PythonCoreBridge.getPublicKey()
        userProfile = UserProfile(
            id = newDeviceId,
            displayName = android.os.Build.MODEL.ifBlank { "Offline Node" },
            publicKey = newPubKey,
            bio = "Encrypted BLE Mesh Node • Offline First"
        )
        storageStats = StorageStats(usedBytes = 0L, messagesBytes = 0L, imagesBytes = 0L, voiceBytes = 0L, otherBytes = 0L)
        activeConversationId = null
        showPanicDialog = false
        screenStack.clear()
        currentScreen = Screen.Home
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }
}
