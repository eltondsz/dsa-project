package com.app.chat.viewmodel

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    val peerAddressMap = java.util.concurrent.ConcurrentHashMap<String, String>()

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
                val existing = nearbyPeers.find { it.id == peer.id }
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
                    nearbyPeers[idx] = peer
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
            if (code != 0) return

            val packetType = (result["packet_type"] as? Number)?.toInt()
            val senderId = result["sender_id"]?.toString() ?: senderAddress ?: "unknown"

            if (senderAddress != null && senderId != "unknown") {
                peerAddressMap[senderId] = senderAddress
                peerAddressMap[senderAddress] = senderId
            }

            if (packetType == 1) { // MESSAGE
                val msgId = result["msg_id"]?.toString() ?: UUID.randomUUID().toString()
                val messageText = result["message"]?.toString() ?: ""
                val timestamp = getCurrentTime()

                val isVoice = messageText.startsWith("[VOICE:") && messageText.endsWith("]")
                val isImage = messageText.startsWith("[IMG:") && messageText.endsWith("]")

                val mediaType = when {
                    isVoice -> MediaType.Voice
                    isImage -> MediaType.Image
                    else -> MediaType.Text
                }
                val mediaDuration = if (isVoice) messageText.removePrefix("[VOICE:").removeSuffix("]").trim() else null
                val mediaThumbnail = if (isImage) messageText.removePrefix("[IMG:").removeSuffix("]").trim() else null
                val displaySummary = when {
                    isVoice -> "🎙 Voice message ($mediaDuration)"
                    isImage -> "📷 Photo"
                    else -> messageText
                }

                viewModelScope.launch {
                    val convId = "chat-$senderId"
                    val existingConv = conversations.find { it.id == convId || (senderAddress != null && it.id == "chat-$senderAddress") }
                    val targetConvId = existingConv?.id ?: convId
                    val peerName = nearbyPeers.find { it.id == senderId || it.id == senderAddress }?.name
                        ?: bleMeshManager?.discoveredPeersMap?.get(senderAddress)?.name
                        ?: "Peer ${senderId.take(6)}"

                    if (existingConv == null) {
                        val newConv = ChatConversation(
                            id = targetConvId,
                            name = peerName,
                            lastMessage = displaySummary,
                            lastTimestamp = "Just now",
                            unreadCount = 1,
                            isGroup = false,
                            avatarInitials = peerName.take(1).uppercase(),
                            isOnline = true
                        )
                        conversations.add(0, newConv)
                    } else {
                        val idx = conversations.indexOf(existingConv)
                        conversations.removeAt(idx)
                        conversations.add(0, existingConv.copy(
                            lastMessage = displaySummary,
                            lastTimestamp = "Just now",
                            unreadCount = if (activeConversationId == targetConvId) 0 else existingConv.unreadCount + 1
                        ))
                    }

                    val list = messagesMap.getOrPut(targetConvId) { mutableStateListOf() }
                    list.add(
                        MessageItem(
                            id = msgId,
                            conversationId = targetConvId,
                            senderId = senderId,
                            senderName = peerName,
                            text = if (mediaType == MediaType.Image) "Photo" else displaySummary,
                            timestamp = timestamp,
                            isIncoming = true,
                            status = MessageStatus.Delivered,
                            mediaType = mediaType,
                            mediaDuration = mediaDuration,
                            mediaThumbnail = mediaThumbnail
                        )
                    )
                }
            } else {
                // Handshake processed: reply with handshake so both devices share keys!
                Log.i("ChatViewModel", "Handshake received from $senderId, sending handshake reply")
                val replyHex = PythonCoreBridge.prepareHandshakePacket()
                if (replyHex != null) {
                    val replyBytes = hexStringToByteArray(replyHex)
                    if (senderAddress != null) {
                        bleMeshManager?.sendPayloadToPeer(senderAddress, replyBytes)
                    } else {
                        bleMeshManager?.broadcastPacket(replyBytes)
                    }
                }

                viewModelScope.launch {
                    val backendPeers = PythonCoreBridge.getPeers()
                    if (backendPeers.isNotEmpty()) {
                        backendPeers.forEach { p ->
                            val id = p["id"]?.toString() ?: return@forEach
                            val name = p["name"]?.toString() ?: "Mesh Peer"
                            val dist = p["distance"]?.toString() ?: "~ 5 m"
                            val rssi = (p["rssi"] as? Number)?.toInt() ?: -60
                            val existing = nearbyPeers.find { it.id == id || (senderAddress != null && it.id == senderAddress) }
                            if (existing == null) {
                                nearbyPeers.add(PeerDevice(id = id, name = name, distanceText = dist, distanceMeters = 5f, rssi = rssi))
                            }
                        }
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
        val existingConv = conversations.find { it.id == "chat-${peer.id}" || it.name == peer.name }
        if (existingConv != null) {
            openConversation(existingConv.id)
        } else {
            val newConv = ChatConversation(
                id = "chat-${peer.id}",
                name = peer.name,
                lastMessage = "",
                lastTimestamp = "Just now",
                unreadCount = 0,
                isGroup = false,
                avatarInitials = peer.name.take(1).uppercase(),
                isOnline = true
            )
            conversations.add(0, newConv)
            messagesMap[newConv.id] = mutableStateListOf()
            openConversation(newConv.id)
        }

        // Transmit handshake packet over BLE to initiate cryptographic key exchange
        val handshakeHex = PythonCoreBridge.prepareHandshakePacket()
        if (handshakeHex != null) {
            try {
                bleMeshManager?.sendPayloadToPeer(peer.id, hexStringToByteArray(handshakeHex))
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending handshake to peer ${peer.id}: ${e.message}")
            }
        }
    }

    private fun sendRawPayload(rawText: String, convId: String): Boolean {
        val rawTarget = conversations.find { it.id == convId }?.id?.removePrefix("chat-") ?: convId
        val targetPeerId = peerAddressMap[rawTarget] ?: rawTarget

        var packetHex = PythonCoreBridge.prepareOutgoingMessage(targetPeerId, rawText)
        if (packetHex == null && rawTarget != targetPeerId) {
            packetHex = PythonCoreBridge.prepareOutgoingMessage(rawTarget, rawText)
        }

        val bleTargetAddress = when {
            BluetoothAdapter.checkBluetoothAddress(rawTarget) -> rawTarget
            BluetoothAdapter.checkBluetoothAddress(targetPeerId) -> targetPeerId
            else -> peerAddressMap[rawTarget] ?: peerAddressMap[targetPeerId]
        }

        // If packetHex is still null, exchange handshake first and retry
        if (packetHex == null) {
            val handshakeHex = PythonCoreBridge.prepareHandshakePacket()
            if (handshakeHex != null) {
                val handshakeBytes = hexStringToByteArray(handshakeHex)
                if (bleTargetAddress != null && BluetoothAdapter.checkBluetoothAddress(bleTargetAddress)) {
                    bleMeshManager?.sendPayloadToPeer(bleTargetAddress, handshakeBytes)
                } else {
                    bleMeshManager?.broadcastPacket(handshakeBytes)
                }
            }
            packetHex = PythonCoreBridge.prepareOutgoingMessage(targetPeerId, rawText)
                ?: PythonCoreBridge.prepareOutgoingMessage(rawTarget, rawText)
        }

        var isTransmitted = false
        if (packetHex != null) {
            try {
                val packetBytes = hexStringToByteArray(packetHex)
                if (bleTargetAddress != null && BluetoothAdapter.checkBluetoothAddress(bleTargetAddress)) {
                    bleMeshManager?.sendPayloadToPeer(bleTargetAddress, packetBytes)
                } else {
                    bleMeshManager?.broadcastPacket(packetBytes)
                }
                isTransmitted = true
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error transmitting packet: ${e.message}")
            }
        }
        return isTransmitted
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || activeConversationId == null) return
        val convId = activeConversationId!!
        val timestamp = getCurrentTime()
        val msgId = UUID.randomUUID().toString()

        val isTransmitted = sendRawPayload(text, convId)

        val sendingMessage = MessageItem(
            id = msgId,
            conversationId = convId,
            senderId = userProfile.id,
            senderName = userProfile.displayName,
            text = text,
            timestamp = timestamp,
            isIncoming = false,
            status = if (isTransmitted) MessageStatus.Sent else MessageStatus.Sending,
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

    fun sendImageMedia(uri: Uri, context: Context) {
        val convId = activeConversationId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap == null) {
                    Log.e("ChatViewModel", "Failed to decode image from uri: $uri")
                    return@launch
                }

                // Downscale to compact thumbnail for fast peer-to-peer BLE transmission
                val maxDim = 48
                val ratio = minOf(maxDim.toFloat() / originalBitmap.width, maxDim.toFloat() / originalBitmap.height, 1.0f)
                val targetW = maxOf(1, (originalBitmap.width * ratio).toInt())
                val targetH = maxOf(1, (originalBitmap.height * ratio).toInt())
                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)

                val outStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 35, outStream)
                val compressedBytes = outStream.toByteArray()
                val b64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

                val imgPayload = "[IMG:$b64]"
                val isTransmitted = sendRawPayload(imgPayload, convId)

                withContext(Dispatchers.Main) {
                    val timestamp = getCurrentTime()
                    val msgId = UUID.randomUUID().toString()
                    val imageMsg = MessageItem(
                        id = msgId,
                        conversationId = convId,
                        senderId = userProfile.id,
                        senderName = userProfile.displayName,
                        text = "Photo",
                        timestamp = timestamp,
                        isIncoming = false,
                        status = if (isTransmitted) MessageStatus.Sent else MessageStatus.Sending,
                        mediaType = MediaType.Image,
                        mediaThumbnail = b64
                    )
                    messagesMap.getOrPut(convId) { mutableStateListOf() }.add(imageMsg)

                    val convIndex = conversations.indexOfFirst { it.id == convId }
                    if (convIndex >= 0) {
                        val oldConv = conversations[convIndex]
                        conversations.removeAt(convIndex)
                        conversations.add(0, oldConv.copy(lastMessage = "📷 Photo", lastTimestamp = "Just now"))
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending image: ${e.message}", e)
            }
        }
    }

    fun toggleVoiceRecording() {
        if (isRecordingVoice) {
            // Stop and send voice message
            isRecordingVoice = false
            val durationStr = String.format(Locale.getDefault(), "0:%02d", maxOf(1, voiceRecordDurationSeconds))
            voiceRecordDurationSeconds = 0

            val convId = activeConversationId ?: return
            val voicePayload = "[VOICE:$durationStr]"
            val isTransmitted = sendRawPayload(voicePayload, convId)

            val voiceMsg = MessageItem(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                senderId = userProfile.id,
                senderName = userProfile.displayName,
                text = "Voice message ($durationStr)",
                timestamp = getCurrentTime(),
                isIncoming = false,
                status = if (isTransmitted) MessageStatus.Sent else MessageStatus.Sending,
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
