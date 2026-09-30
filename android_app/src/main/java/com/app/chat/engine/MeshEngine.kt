package com.app.chat.engine

import com.app.chat.protocol.AnnouncementPayload
import com.app.chat.protocol.BitchatPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Collections
import java.util.UUID

data class PeerInfo(
    val peerIdHex: String,
    val nickname: String,
    val hopCount: Int,
    val isDirect: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderNickname: String,
    val senderIdHex: String,
    val recipientIdHex: String? = null,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hopCount: Int = 1,
    val isDirect: Boolean = true,
    val isSelf: Boolean = false,
    val isPrivate: Boolean = false,
    val isSystem: Boolean = false
)

class MeshEngine(
    private val scope: CoroutineScope,
    initialNickname: String? = null,
    savedPeerId: ByteArray? = null
) {
    // 8-byte unique local peer ID (16 hex chars)
    val localPeerId: ByteArray = savedPeerId ?: ByteArray(8).apply { SecureRandom().nextBytes(this) }
    val localPeerIdHex: String = localPeerId.joinToString("") { "%02x".format(it) }

    var nickname: String = initialNickname?.ifBlank { null } ?: "Node-${localPeerIdHex.takeLast(4)}"
        private set

    private val _peers = MutableStateFlow<Map<String, PeerInfo>>(emptyMap())
    val peers: StateFlow<Map<String, PeerInfo>> = _peers.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _directLinksCount = MutableStateFlow(0)
    val directLinksCount: StateFlow<Int> = _directLinksCount.asStateFlow()

    // Deduplication seen-cache: stores packet hash -> timestamp
    private val seenPackets = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(1000, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 1000
            }
        }
    )

    // Callback invoked when a packet needs to be sent out over the link layer
    var onSendPacket: ((bytes: ByteArray, excludeLinkId: String?) -> Unit)? = null

    // Callback when a pong response is received with round-trip latency and hop count
    var onPingResult: ((peerNick: String, peerIdHex: String, hopCount: Int, latencyMs: Long) -> Unit)? = null

    // Callback when a chat message or peer is received for external persistence/ViewModel sync
    var onMessageReceived: ((ChatMessage) -> Unit)? = null
    var onPeerDiscoveredOrUpdated: ((PeerInfo) -> Unit)? = null

    init {
        // Start periodic announce loop (every 12 seconds)
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(12000)
                broadcastAnnounce()
                pruneStalePeers()
            }
        }

        // Post welcome system message
        addSystemMessage("Mesh engine initialized. Node ID: ${localPeerIdHex.take(8)} as '$nickname'")
    }

    fun updateNickname(newName: String) {
        val trimmed = newName.trim().take(30)
        if (trimmed.isNotEmpty() && trimmed != nickname) {
            nickname = trimmed
            addSystemMessage("Nickname updated to '$nickname'")
            broadcastAnnounce()
        }
    }

    fun updateDirectLinksCount(count: Int) {
        _directLinksCount.value = count
    }

    /**
     * Broadcasts an Announce packet to discover and notify peers of our presence.
     */
    fun broadcastAnnounce() {
        val directNeighbors = _peers.value.values
            .filter { it.isDirect }
            .map { hexToBytes(it.peerIdHex) }

        val payload = AnnouncementPayload.encode(nickname, directNeighbors)
        val packet = BitchatPacket(
            version = BitchatPacket.VERSION,
            type = BitchatPacket.TYPE_ANNOUNCE,
            ttl = BitchatPacket.DEFAULT_TTL,
            senderID = localPeerId,
            recipientID = null,
            payload = payload
        )

        markPacketSeen(packet)
        val encoded = BitchatPacket.encode(packet)
        onSendPacket?.invoke(encoded, null)
    }

    /**
     * Sends a public chat message to all nodes in the mesh.
     */
    fun sendPublicMessage(text: String): ChatMessage {
        val payload = text.toByteArray(Charsets.UTF_8)
        val packet = BitchatPacket(
            version = BitchatPacket.VERSION,
            type = BitchatPacket.TYPE_MESSAGE,
            ttl = BitchatPacket.DEFAULT_TTL,
            senderID = localPeerId,
            recipientID = null,
            payload = payload
        )

        markPacketSeen(packet)
        val msg = ChatMessage(
            senderNickname = nickname,
            senderIdHex = localPeerIdHex,
            recipientIdHex = null,
            text = text,
            hopCount = 0,
            isDirect = true,
            isSelf = true,
            isPrivate = false
        )
        _messages.update { current -> current + msg }
        onMessageReceived?.invoke(msg)

        val encoded = BitchatPacket.encode(packet)
        onSendPacket?.invoke(encoded, null)
        return msg
    }

    /**
     * Sends a private, addressed message to a specific peer.
     */
    fun sendDirectMessage(targetPeerIdHex: String, text: String): ChatMessage? {
        val targetBytes = hexToBytes(targetPeerIdHex)
        if (targetBytes.size != 8) {
            addSystemMessage("Invalid Peer ID: must be 16 hex characters (8 bytes)")
            return null
        }

        val payload = text.toByteArray(Charsets.UTF_8)
        val packet = BitchatPacket(
            version = BitchatPacket.VERSION,
            type = BitchatPacket.TYPE_DIRECT_MESSAGE,
            ttl = BitchatPacket.DEFAULT_TTL,
            flags = BitchatPacket.FLAG_HAS_RECIPIENT,
            senderID = localPeerId,
            recipientID = targetBytes,
            payload = payload
        )

        markPacketSeen(packet)
        val msg = ChatMessage(
            senderNickname = nickname,
            senderIdHex = localPeerIdHex,
            recipientIdHex = targetPeerIdHex.lowercase(),
            text = text,
            hopCount = 0,
            isDirect = true,
            isSelf = true,
            isPrivate = true
        )
        _messages.update { current -> current + msg }
        onMessageReceived?.invoke(msg)

        val encoded = BitchatPacket.encode(packet)
        onSendPacket?.invoke(encoded, null)
        return msg
    }

    /**
     * Sends a diagnostic ping probe to a specific peer to measure latency & hops.
     */
    fun sendPing(targetPeerIdHex: String) {
        val targetBytes = hexToBytes(targetPeerIdHex)
        if (targetBytes.size != 8) {
            addSystemMessage("Invalid Peer ID for ping: must be 16 hex characters")
            return
        }

        val packet = BitchatPacket(
            version = BitchatPacket.VERSION,
            type = BitchatPacket.TYPE_PING,
            ttl = BitchatPacket.DEFAULT_TTL,
            flags = BitchatPacket.FLAG_HAS_RECIPIENT,
            senderID = localPeerId,
            recipientID = targetBytes,
            payload = ByteArray(0)
        )

        markPacketSeen(packet)
        val targetNick = _peers.value[targetPeerIdHex.lowercase()]?.nickname ?: targetPeerIdHex.take(8)
        addSystemMessage("Sending ping probe to '$targetNick' (${targetPeerIdHex.take(8)})…")
        val encoded = BitchatPacket.encode(packet)
        onSendPacket?.invoke(encoded, null)
    }

    /**
     * Core mesh ingestion pipeline:
     * 1. Decodes packet.
     * 2. Checks deduplication cache to drop duplicates and loops.
     * 3. Calculates hop distance based on TTL.
     * 4. Delivers locally if addressed to us or broadcast.
     * 5. Performs Split-Horizon flood relay if TTL > 1.
     */
    fun processInboundPacket(rawBytes: ByteArray, ingressLinkId: String?) {
        val packet = BitchatPacket.decode(rawBytes) ?: return

        // Drop our own echoed broadcasts
        if (packet.senderID.contentEquals(localPeerId)) return

        // Deduplication check
        val key = packetSignatureKey(packet)
        if (seenPackets.containsKey(key)) return
        seenPackets[key] = System.currentTimeMillis()

        val senderHex = bytesToHex(packet.senderID).lowercase()
        val hopCount = maxOf(1, (BitchatPacket.DEFAULT_TTL - packet.ttl + 1).toInt())
        val isDirectLink = (ingressLinkId != null && hopCount == 1)

        val isForUs = (packet.recipientID == null || packet.recipientID.contentEquals(localPeerId))

        if (isForUs) {
            handlePacketLocally(packet, senderHex, hopCount, isDirectLink)
        }

        // Flood relay if TTL allows (multi-hop forwarding)
        if (packet.ttl > 1) {
            val relayedPacket = packet.copy(ttl = (packet.ttl - 1).toByte())
            val relayBytes = BitchatPacket.encode(relayedPacket)
            // Split-Horizon: do not relay back on the ingress link!
            onSendPacket?.invoke(relayBytes, ingressLinkId)
        }
    }

    private fun handlePacketLocally(
        packet: BitchatPacket,
        senderHex: String,
        hopCount: Int,
        isDirectLink: Boolean
    ) {
        when (packet.type) {
            BitchatPacket.TYPE_ANNOUNCE -> {
                val announcedNick = AnnouncementPayload.decodeNickname(packet.payload) ?: senderHex.take(8)
                val isNew = !_peers.value.containsKey(senderHex)

                val peerInfo = PeerInfo(
                    peerIdHex = senderHex,
                    nickname = announcedNick,
                    hopCount = hopCount,
                    isDirect = isDirectLink,
                    lastSeen = System.currentTimeMillis()
                )

                _peers.update { current ->
                    current + (senderHex to peerInfo)
                }
                onPeerDiscoveredOrUpdated?.invoke(peerInfo)

                if (isNew) {
                    val path = if (isDirectLink) "direct link" else "$hopCount hops away"
                    addSystemMessage("Peer discovered: '$announcedNick' (${senderHex.take(8)}) via $path")
                }
            }

            BitchatPacket.TYPE_MESSAGE -> {
                val text = String(packet.payload, Charsets.UTF_8)
                val senderNick = _peers.value[senderHex]?.nickname ?: senderHex.take(8)

                val msg = ChatMessage(
                    senderNickname = senderNick,
                    senderIdHex = senderHex,
                    recipientIdHex = null,
                    text = text,
                    timestamp = packet.timestamp,
                    hopCount = hopCount,
                    isDirect = isDirectLink,
                    isSelf = false,
                    isPrivate = false
                )
                _messages.update { current -> current + msg }
                onMessageReceived?.invoke(msg)
            }

            BitchatPacket.TYPE_DIRECT_MESSAGE -> {
                val text = String(packet.payload, Charsets.UTF_8)
                val senderNick = _peers.value[senderHex]?.nickname ?: senderHex.take(8)

                val msg = ChatMessage(
                    senderNickname = senderNick,
                    senderIdHex = senderHex,
                    recipientIdHex = localPeerIdHex,
                    text = text,
                    timestamp = packet.timestamp,
                    hopCount = hopCount,
                    isDirect = isDirectLink,
                    isSelf = false,
                    isPrivate = true
                )
                _messages.update { current -> current + msg }
                onMessageReceived?.invoke(msg)
            }

            BitchatPacket.TYPE_PING -> {
                // Reply with PONG addressed to sender
                val pong = BitchatPacket(
                    version = BitchatPacket.VERSION,
                    type = BitchatPacket.TYPE_PONG,
                    ttl = BitchatPacket.DEFAULT_TTL,
                    flags = BitchatPacket.FLAG_HAS_RECIPIENT,
                    senderID = localPeerId,
                    recipientID = packet.senderID,
                    payload = ByteArray(0)
                )
                markPacketSeen(pong)
                val encoded = BitchatPacket.encode(pong)
                onSendPacket?.invoke(encoded, null)
            }

            BitchatPacket.TYPE_PONG -> {
                val senderNick = _peers.value[senderHex]?.nickname ?: senderHex.take(8)
                val latency = System.currentTimeMillis() - packet.timestamp
                val pongText = "Pong received from $senderNick (${senderHex.take(8)}) — $hopCount hop(s) ($latency ms)"
                addSystemMessage(pongText)
                onPingResult?.invoke(senderNick, senderHex, hopCount, latency)
            }

            BitchatPacket.TYPE_LEAVE -> {
                _peers.update { current -> current - senderHex }
                addSystemMessage("Peer ${senderHex.take(8)} left the mesh")
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        addSystemMessage("Chat history cleared")
    }

    fun addSystemMessage(text: String) {
        val sysMsg = ChatMessage(
            senderNickname = "SYSTEM",
            senderIdHex = "0000000000000000",
            text = text,
            hopCount = 0,
            isDirect = true,
            isSelf = false,
            isPrivate = false,
            isSystem = true
        )
        _messages.update { current -> current + sysMsg }
        onMessageReceived?.invoke(sysMsg)
    }

    private fun pruneStalePeers() {
        val now = System.currentTimeMillis()
        val staleThreshold = 60000L // 60 seconds of silence
        _peers.update { current ->
            current.filterValues { now - it.lastSeen < staleThreshold }
        }
    }

    private fun markPacketSeen(packet: BitchatPacket) {
        seenPackets[packetSignatureKey(packet)] = System.currentTimeMillis()
    }

    private fun packetSignatureKey(packet: BitchatPacket): String {
        return "${bytesToHex(packet.senderID)}_${packet.timestamp}_${packet.type}_${packet.payload.contentHashCode()}"
    }

    companion object {
        fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun hexToBytes(hex: String): ByteArray {
            val cleanHex = hex.trim().replace(":", "").replace(" ", "")
            val len = cleanHex.length
            val result = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                result[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                        Character.digit(cleanHex[i + 1], 16)).toByte()
            }
            return result
        }
    }
}
