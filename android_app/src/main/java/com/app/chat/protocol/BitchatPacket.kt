package com.app.chat.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Core packet structure for BitChat mesh communication.
 * Adheres to BitChat wire protocol specifications:
 * Header:
 * - Version (1 byte): 0x01
 * - Type (1 byte): 0x01=Announce, 0x02=Message, 0x03=Leave, 0x11=DirectMessage, 0x26=Ping, 0x27=Pong
 * - TTL (1 byte): Hop limit (default 7)
 * - Timestamp (8 bytes): Milliseconds since epoch (big-endian)
 * - Flags (1 byte): 0x01 = hasRecipient, 0x02 = hasSignature
 * - PayloadLength (2 bytes): uint16 length (big-endian)
 * Variable:
 * - SenderID (8 bytes)
 * - RecipientID (8 bytes, if hasRecipient flag set)
 * - Payload (Variable bytes)
 */
data class BitchatPacket(
    val version: Byte = VERSION,
    val type: Byte,
    var ttl: Byte = DEFAULT_TTL,
    val timestamp: Long = System.currentTimeMillis(),
    val flags: Byte = 0,
    val senderID: ByteArray, // 8 bytes
    val recipientID: ByteArray? = null, // 8 bytes if directed
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BitchatPacket
        if (version != other.version) return false
        if (type != other.type) return false
        if (ttl != other.ttl) return false
        if (timestamp != other.timestamp) return false
        if (flags != other.flags) return false
        if (!senderID.contentEquals(other.senderID)) return false
        if (recipientID != null) {
            if (other.recipientID == null || !recipientID.contentEquals(other.recipientID)) return false
        } else if (other.recipientID != null) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type
        result = 31 * result + ttl
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + flags
        result = 31 * result + senderID.contentHashCode()
        result = 31 * result + (recipientID?.contentHashCode() ?: 0)
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val VERSION: Byte = 1
        const val DEFAULT_TTL: Byte = 7

        const val TYPE_ANNOUNCE: Byte = 0x01
        const val TYPE_MESSAGE: Byte = 0x02
        const val TYPE_LEAVE: Byte = 0x03
        const val TYPE_DIRECT_MESSAGE: Byte = 0x11
        const val TYPE_PING: Byte = 0x26
        const val TYPE_PONG: Byte = 0x27

        const val FLAG_HAS_RECIPIENT: Byte = 0x01

        fun encode(packet: BitchatPacket): ByteArray {
            val hasRecipient = packet.recipientID != null && packet.recipientID.size == 8
            var flags = packet.flags.toInt()
            if (hasRecipient) {
                flags = flags or FLAG_HAS_RECIPIENT.toInt()
            }
            val payloadLen = packet.payload.size
            val totalSize = 14 + 8 + (if (hasRecipient) 8 else 0) + payloadLen

            val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
            buffer.put(packet.version)
            buffer.put(packet.type)
            buffer.put(packet.ttl)
            buffer.putLong(packet.timestamp)
            buffer.put(flags.toByte())
            buffer.putShort(payloadLen.toShort())

            // Sender ID (8 bytes)
            if (packet.senderID.size == 8) {
                buffer.put(packet.senderID)
            } else {
                val padded = ByteArray(8)
                System.arraycopy(packet.senderID, 0, padded, 0, minOf(packet.senderID.size, 8))
                buffer.put(padded)
            }

            // Recipient ID (8 bytes)
            if (hasRecipient) {
                buffer.put(packet.recipientID!!)
            }

            // Payload
            buffer.put(packet.payload)

            return buffer.array()
        }

        fun decode(data: ByteArray): BitchatPacket? {
            if (data.size < 22) return null // 14 byte header + 8 byte senderID minimum
            return try {
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                val version = buffer.get()
                val type = buffer.get()
                val ttl = buffer.get()
                val timestamp = buffer.long
                val flags = buffer.get()
                val payloadLen = buffer.short.toInt() and 0xFFFF

                val hasRecipient = (flags.toInt() and FLAG_HAS_RECIPIENT.toInt()) != 0
                val expectedMinSize = 14 + 8 + (if (hasRecipient) 8 else 0) + payloadLen
                if (data.size < expectedMinSize) return null

                val senderID = ByteArray(8)
                buffer.get(senderID)

                val recipientID = if (hasRecipient) {
                    val rID = ByteArray(8)
                    buffer.get(rID)
                    rID
                } else null

                val payload = ByteArray(payloadLen)
                buffer.get(payload)

                BitchatPacket(
                    version = version,
                    type = type,
                    ttl = ttl,
                    timestamp = timestamp,
                    flags = flags,
                    senderID = senderID,
                    recipientID = recipientID,
                    payload = payload
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * TLV helper for BitChat Announce payloads
 */
object AnnouncementPayload {
    private const val TLV_NICKNAME: Byte = 0x01
    private const val TLV_NEIGHBORS: Byte = 0x04

    fun encode(nickname: String, neighbors: List<ByteArray> = emptyList()): ByteArray {
        val nickBytes = nickname.toByteArray(Charsets.UTF_8).let {
            if (it.size > 255) it.copyOf(255) else it
        }
        var totalSize = 2 + nickBytes.size
        val validNeighbors = neighbors.filter { it.size == 8 }.take(10)
        if (validNeighbors.isNotEmpty()) {
            totalSize += 2 + validNeighbors.size * 8
        }

        val buffer = ByteBuffer.allocate(totalSize)
        // TLV Nickname
        buffer.put(TLV_NICKNAME)
        buffer.put(nickBytes.size.toByte())
        buffer.put(nickBytes)

        // TLV Neighbors
        if (validNeighbors.isNotEmpty()) {
            buffer.put(TLV_NEIGHBORS)
            buffer.put((validNeighbors.size * 8).toByte())
            for (neighbor in validNeighbors) {
                buffer.put(neighbor)
            }
        }

        return buffer.array()
    }

    fun decodeNickname(payload: ByteArray): String? {
        try {
            var i = 0
            while (i + 2 <= payload.size) {
                val type = payload[i]
                val length = payload[i + 1].toInt() and 0xFF
                i += 2
                if (i + length > payload.size) break
                if (type == TLV_NICKNAME) {
                    return String(payload, i, length, Charsets.UTF_8)
                }
                i += length
            }
        } catch (_: Exception) {}
        return null
    }
}

