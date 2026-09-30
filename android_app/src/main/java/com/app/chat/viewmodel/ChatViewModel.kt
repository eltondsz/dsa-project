package com.app.chat.viewmodel

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
import com.app.chat.engine.ChatMessage
import com.app.chat.engine.MeshEngine
import com.app.chat.engine.PeerInfo
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
import java.util.concurrent.ConcurrentHashMap

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
            bio = "Encrypted BLE Mesh Node • BitChat Mesh Active"
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
    var pingNotification by mutableStateOf<String?>(null)

    val mnemonicWords = listOf(
        "anchor", "cipher", "beacon", "mesh", "signal", "quantum",
        "forest", "shield", "pulse", "matrix", "vector", "frost"
    )

    val conversations = mutableStateListOf<ChatConversation>()
    val messagesMap = mutableStateMapOf<String, MutableList<MessageItem>>()
    val nearbyPeers = mutableStateListOf<PeerDevice>()
    val selectedGroupPeerIds = mutableStateListOf<String>()
    var newGroupName by mutableStateOf("")

    val peerAddressMap = ConcurrentHashMap<String, String>()

    // Core BitChat Mesh Routing Engine
    val meshEngine = MeshEngine(viewModelScope)

    var bleMeshManager: BleMeshManager? = null
        private set

    var isBluetoothEnabled by mutableStateOf(false)
    var onPromptEnableBluetooth: (() -> Unit)? = null

    init {
        setupMeshCallbacks()
        loadInitialData()
    }

    private fun setupMeshCallbacks() {
        // Peer discovery/update callback from BitChat mesh
        meshEngine.onPeerDiscoveredOrUpdated = { peer: PeerInfo ->
            viewModelScope.launch(Dispatchers.Main) {
                val peerId = peer.peerIdHex
                val peerName = peer.nickname
                val distText = if (peer.isDirect) "Direct (1 hop)" else "${peer.hopCount} hops"
                val existing = nearbyPeers.find { it.id == peerId }
                if (existing == null) {
                    nearbyPeers.add(
                        PeerDevice(
                            id = peerId,
                            name = peerName,
                            distanceText = distText,
                            distanceMeters = if (peer.isDirect) 3f else 15f,
                            rssi = -60,
                            isOnline = true,
                            hopCount = peer.hopCount,
                            isDirect = peer.isDirect
                        )
                    )
                } else {
                    val idx = nearbyPeers.indexOf(existing)
                    nearbyPeers[idx] = existing.copy(
                        name = peerName,
                        distanceText = distText,
                        hopCount = peer.hopCount,
                        isDirect = peer.isDirect,
                        isOnline = true
                    )
                }
            }
        }

        // Inbound / Outbound message callback from BitChat mesh
        meshEngine.onMessageReceived = { chatMsg: ChatMessage ->
            viewModelScope.launch(Dispatchers.Main) {
                handleChatMessage(chatMsg)
            }
        }

        // Diagnostic ping result callback
        meshEngine.onPingResult = { peerNick: String, peerIdHex: String, hopCount: Int, latencyMs: Long ->
            viewModelScope.launch(Dispatchers.Main) {
                val notice = "Ping response from $peerNick (${peerIdHex.take(8)}): ${latencyMs}ms • $hopCount hop(s)"
                pingNotification = notice

                val targetConvId = activeConversationId ?: "chat-$peerIdHex"
                val list = messagesMap.getOrPut(targetConvId) { mutableStateListOf() }
                list.add(
                    MessageItem(
                        id = UUID.randomUUID().toString(),
                        conversationId = targetConvId,
                        senderId = peerIdHex,
                        senderName = peerNick,
                        text = "🏓 $notice",
                        timestamp = getCurrentTime(),
                        isIncoming = true,
                        status = MessageStatus.Delivered,
                        hopCount = hopCount,
                        isDirect = (hopCount <= 1),
                        isSystem = true
                    )
                )
            }
        }
    }

    private fun handleChatMessage(chatMsg: ChatMessage) {
        val isPrivate = chatMsg.isPrivate
        val partnerId = if (chatMsg.isSelf) {
            chatMsg.recipientIdHex ?: "unknown"
        } else {
            chatMsg.senderIdHex
        }

        val targetConvId = if (isPrivate) "chat-$partnerId" else "mesh-public"
        val isIncoming = !chatMsg.isSelf
        val rawText = chatMsg.text

        val isVoice = rawText.startsWith("[VOICE:") && rawText.endsWith("]")
        val isImage = rawText.startsWith("[IMG:") && rawText.endsWith("]")

        val mediaType = when {
            isVoice -> MediaType.Voice
            isImage -> MediaType.Image
            else -> MediaType.Text
        }
        val mediaDuration = if (isVoice) rawText.removePrefix("[VOICE:").removeSuffix("]").trim() else null
        val mediaThumbnail = if (isImage) rawText.removePrefix("[IMG:").removeSuffix("]").trim() else null
        val displayText = when {
            isVoice -> "🎙 Voice note ($mediaDuration)"
            isImage -> "📷 Photo"
            else -> rawText
        }

        val timeStr = getCurrentTime()
        val item = MessageItem(
            id = chatMsg.id,
            conversationId = targetConvId,
            senderId = chatMsg.senderIdHex,
            senderName = chatMsg.senderNickname,
            text = displayText,
            timestamp = timeStr,
            isIncoming = isIncoming,
            status = MessageStatus.Delivered,
            mediaType = mediaType,
            mediaDuration = mediaDuration,
            mediaThumbnail = mediaThumbnail,
            hopCount = chatMsg.hopCount,
            isDirect = chatMsg.isDirect,
            isSystem = chatMsg.isSystem
        )

        val list = messagesMap.getOrPut(targetConvId) { mutableStateListOf() }
        if (list.none { it.id == item.id }) {
            list.add(item)
        }

        // Sync conversation preview in conversations list
        val convName = when {
            targetConvId == "mesh-public" -> "Public Mesh Broadcast"
            else -> nearbyPeers.find { it.id == partnerId }?.name ?: "Node ${partnerId.take(6)}"
        }

        val existingConv = conversations.find { it.id == targetConvId }
        if (existingConv == null) {
            conversations.add(
                0,
                ChatConversation(
                    id = targetConvId,
                    name = convName,
                    lastMessage = displayText,
                    lastTimestamp = "Just now",
                    unreadCount = if (activeConversationId == targetConvId) 0 else 1,
                    isGroup = (targetConvId == "mesh-public"),
                    avatarInitials = if (targetConvId == "mesh-public") "M" else convName.take(1).uppercase(),
                    isOnline = true,
                    membersCount = if (targetConvId == "mesh-public") maxOf(1, nearbyPeers.size + 1) else 1,
                    peerIdHex = if (targetConvId == "mesh-public") null else partnerId
                )
            )
        } else {
            val idx = conversations.indexOf(existingConv)
            conversations.removeAt(idx)
            conversations.add(
                0,
                existingConv.copy(
                    lastMessage = displayText,
                    lastTimestamp = "Just now",
                    unreadCount = if (activeConversationId == targetConvId) 0 else existingConv.unreadCount + 1,
                    isOnline = true
                )
            )
        }

        // Persist to Python SQLite database
        PythonCoreBridge.recordChatMessage(
            chatMsg.senderIdHex,
            chatMsg.recipientIdHex,
            rawText,
            isIncoming
        )
    }

    fun checkBluetoothStatus() {
        isBluetoothEnabled = bleMeshManager?.isBluetoothEnabled == true
    }

    fun requestEnableBluetooth() {
        if (bleMeshManager?.enableBluetoothDirectly() == true) {
            isBluetoothEnabled = true
            bleMeshManager?.startMesh()
        } else {
            onPromptEnableBluetooth?.invoke()
        }
    }

    fun setBleManager(manager: BleMeshManager) {
        this.bleMeshManager = manager
        checkBluetoothStatus()
    }

    fun toggleScanning() {
        isScanningNearby = !isScanningNearby
        if (isScanningNearby) {
            bleMeshManager?.startMesh()
        } else {
            bleMeshManager?.stopMesh()
        }
    }

    fun loadInitialData() {
        val pubKey = PythonCoreBridge.getPublicKey()
        val defaultName = android.os.Build.MODEL.ifBlank { "Node ${meshEngine.localPeerIdHex.take(4)}" }

        userProfile = userProfile.copy(
            id = meshEngine.localPeerIdHex,
            displayName = if (userProfile.displayName.isBlank() || userProfile.displayName == "Offline Node") defaultName else userProfile.displayName,
            publicKey = pubKey
        )
        meshEngine.updateNickname(userProfile.displayName)

        // Storage metrics
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

        // Default: Add "Public Mesh Broadcast" so users can chat immediately
        val publicMeshConversation = ChatConversation(
            id = "mesh-public",
            name = "Public Mesh Broadcast",
            lastMessage = "Ready to send and relay offline Bluetooth messages.",
            lastTimestamp = "Now",
            unreadCount = 0,
            isGroup = true,
            avatarInitials = "M",
            isOnline = true,
            membersCount = 1
        )
        conversations.add(publicMeshConversation)
        messagesMap["mesh-public"] = mutableStateListOf()

        // Load historical peers & messages from SQLite
        val backendPeers = PythonCoreBridge.getPeers()
        backendPeers.forEach { p ->
            val id = p["id"]?.toString() ?: return@forEach
            if (id == "test-recipient" || id == "peer1" || id == userProfile.id) return@forEach
            val name = p["name"]?.toString() ?: "Node ${id.take(6)}"
            val dist = p["distance"]?.toString() ?: "~ 5 m"
            val rssi = (p["rssi"] as? Number)?.toInt() ?: -60
            val peer = PeerDevice(id = id, name = name, distanceText = dist, distanceMeters = 5f, rssi = rssi)
            if (nearbyPeers.none { it.id == id }) {
                nearbyPeers.add(peer)
            }

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
                        isOnline = true,
                        peerIdHex = id
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
        val convId = "chat-${peer.id}"
        val existingConv = conversations.find { it.id == convId || it.name == peer.name }
        if (existingConv != null) {
            openConversation(existingConv.id)
        } else {
            val newConv = ChatConversation(
                id = convId,
                name = peer.name,
                lastMessage = "",
                lastTimestamp = "Just now",
                unreadCount = 0,
                isGroup = false,
                avatarInitials = peer.name.take(1).uppercase(),
                isOnline = true,
                peerIdHex = peer.id
            )
            conversations.add(0, newConv)
            messagesMap[newConv.id] = mutableStateListOf()
            openConversation(newConv.id)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || activeConversationId == null) return
        val convId = activeConversationId!!

        // Handle slash commands
        if (text.startsWith("/")) {
            val parts = text.substring(1).trim().split(" ", limit = 3)
            when (parts[0].lowercase()) {
                "nick" -> {
                    if (parts.size > 1) {
                        meshEngine.updateNickname(parts[1])
                        userProfile = userProfile.copy(displayName = parts[1])
                    }
                    return
                }
                "msg" -> {
                    if (parts.size >= 3) {
                        meshEngine.sendDirectMessage(parts[1], parts[2])
                    }
                    return
                }
                "ping" -> {
                    if (parts.size >= 2) {
                        meshEngine.sendPing(parts[1])
                    }
                    return
                }
                "clear" -> {
                    messagesMap[convId]?.clear()
                    meshEngine.clearMessages()
                    return
                }
                "help" -> {
                    val helpText = "Available commands:\n• /nick <name>\n• /msg <peerIdHex> <text>\n• /ping <peerIdHex>\n• /clear"
                    val list = messagesMap.getOrPut(convId) { mutableStateListOf() }
                    list.add(
                        MessageItem(
                            id = UUID.randomUUID().toString(),
                            conversationId = convId,
                            senderId = "system",
                            senderName = "System",
                            text = helpText,
                            timestamp = getCurrentTime(),
                            isIncoming = true,
                            status = MessageStatus.Delivered,
                            isSystem = true
                        )
                    )
                    return
                }
            }
        }

        val targetHex = conversations.find { it.id == convId }?.peerIdHex
            ?: convId.removePrefix("chat-")

        val isDirectChat = (convId != "mesh-public" && !convId.startsWith("group-"))
        if (isDirectChat && targetHex.length == 16) {
            meshEngine.sendDirectMessage(targetHex, text)
        } else {
            meshEngine.sendPublicMessage(text)
        }
    }

    fun sendPing(targetPeerIdHex: String) {
        meshEngine.sendPing(targetPeerIdHex)
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
                val targetHex = conversations.find { it.id == convId }?.peerIdHex ?: convId.removePrefix("chat-")
                val isDirectChat = (convId != "mesh-public" && !convId.startsWith("group-"))

                withContext(Dispatchers.Main) {
                    if (isDirectChat && targetHex.length == 16) {
                        meshEngine.sendDirectMessage(targetHex, imgPayload)
                    } else {
                        meshEngine.sendPublicMessage(imgPayload)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending image: ${e.message}", e)
            }
        }
    }

    fun toggleVoiceRecording() {
        if (isRecordingVoice) {
            isRecordingVoice = false
            val durationStr = String.format(Locale.getDefault(), "0:%02d", maxOf(1, voiceRecordDurationSeconds))
            voiceRecordDurationSeconds = 0

            val convId = activeConversationId ?: return
            val voicePayload = "[VOICE:$durationStr]"
            val targetHex = conversations.find { it.id == convId }?.peerIdHex ?: convId.removePrefix("chat-")
            val isDirectChat = (convId != "mesh-public" && !convId.startsWith("group-"))

            if (isDirectChat && targetHex.length == 16) {
                meshEngine.sendDirectMessage(targetHex, voicePayload)
            } else {
                meshEngine.sendPublicMessage(voicePayload)
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
                text = "Group channel created. Broadcasts are relayed across all reachable nodes.",
                timestamp = getCurrentTime(),
                isIncoming = false,
                status = MessageStatus.Delivered,
                isSystem = true
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
        meshEngine.updateNickname(displayName)
        if (isDiscoveryEnabled && isBluetoothEnabled) {
            bleMeshManager?.startMesh()
        } else if (!isDiscoveryEnabled) {
            bleMeshManager?.stopMesh()
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
            bio = "Encrypted BLE Mesh Node • BitChat Mesh Active"
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

    private fun formatTimestamp(rawTimestamp: Any?): String {
        return try {
            val epochSec = (rawTimestamp as? Number)?.toLong() ?: return "Earlier"
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            sdf.format(Date(epochSec * 1000L))
        } catch (_: Exception) {
            "Earlier"
        }
    }
}
