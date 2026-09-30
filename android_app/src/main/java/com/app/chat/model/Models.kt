package com.app.chat.model

enum class Screen {
    Splash,
    Onboarding,
    Permissions,
    MnemonicDisplay,
    Home,
    DirectChat,
    GroupChat,
    Nearby,
    CreateGroup,
    MeshGraph,
    Settings,
    Storage,
    Profile
}

enum class NavigationTab(val label: String, val iconText: String) {
    Chats("Chats", "💬"),
    Nearby("Nearby", "⌾"),
    Groups("Groups", "👥"),
    Settings("Settings", "⚙")
}

enum class MessageStatus {
    Draft,
    Pending,
    Sending,
    Sent,
    Delivered,
    Read,
    Failed
}

enum class MediaType {
    Text,
    Image,
    Voice
}

data class MessageItem(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: String,
    val isIncoming: Boolean,
    val status: MessageStatus = MessageStatus.Delivered,
    val mediaType: MediaType = MediaType.Text,
    val mediaDuration: String? = null,
    val mediaThumbnail: String? = null,
    val hopCount: Int = 1,
    val isDirect: Boolean = true,
    val isSystem: Boolean = false
)

data class ChatConversation(
    val id: String,
    val name: String,
    val lastMessage: String,
    val lastTimestamp: String,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val avatarInitials: String = name.take(1).uppercase(),
    val isOnline: Boolean = true,
    val membersCount: Int = 1,
    val peerIdHex: String? = null
)

data class PeerDevice(
    val id: String,
    val name: String,
    val distanceText: String,
    val distanceMeters: Float = 5f,
    val rssi: Int = -65,
    val isOnline: Boolean = true,
    val publicKey: String = "",
    val hopCount: Int = 1,
    val isDirect: Boolean = true
)

data class UserProfile(
    val id: String = "node-pub-9921",
    val displayName: String = "Node",
    val publicKey: String = "0x89ab...34fe",
    val deviceName: String = "This Device",
    val isDiscoveryEnabled: Boolean = true,
    val bio: String = "Encrypted BLE Mesh Node • BitChat Mesh Active"
)

data class StorageStats(
    val usedBytes: Long = 1_400_000_000L,
    val totalBytes: Long = 5_000_000_000L,
    val messagesBytes: Long = 842_000_000L,
    val imagesBytes: Long = 392_000_000L,
    val voiceBytes: Long = 121_000_000L,
    val otherBytes: Long = 45_000_000L
)

