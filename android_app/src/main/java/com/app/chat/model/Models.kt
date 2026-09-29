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
    val mediaThumbnail: String? = null
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
    val peerAddress: String = "",
    val peerUuid: String = ""
)

data class PeerDevice(
    val id: String,
    val name: String,
    val distanceText: String,
    val distanceMeters: Float = 5f,
    val rssi: Int = -65,
    val isOnline: Boolean = true,
    val publicKey: String = "",
    val bleAddress: String = id,
    val peerUuid: String = ""
)

data class UserProfile(
    val id: String = "",
    val displayName: String = "",
    val publicKey: String = "",
    val deviceName: String = "This Device",
    val isDiscoveryEnabled: Boolean = true,
    val bio: String = "Encrypted BLE Mesh Node • Offline First"
)

data class StorageStats(
    val usedBytes: Long = 0L,
    val totalBytes: Long = 100_000_000L,
    val messagesBytes: Long = 0L,
    val imagesBytes: Long = 0L,
    val voiceBytes: Long = 0L,
    val otherBytes: Long = 0L
)

