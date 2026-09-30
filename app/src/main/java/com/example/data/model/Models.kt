package com.example.data.model

enum class MessageType {
  TEXT, IMAGE, VIDEO, AUDIO, VOICE, FILE, LOCATION
}

enum class MessageDeliveryStatus {
  SENDING, SENT, DELIVERED, READ, FAILED
}

data class User(
  val uid: String = "",
  val displayName: String = "",
  val username: String = "",
  val email: String = "",
  val phoneNumber: String = "",
  val photoUrl: String = "",
  val bio: String = "Fluid connections in real-time.",
  val onlineVisible: Boolean = true,
  val lastSeenVisible: Boolean = true,
  val isOnline: Boolean = false,
  val lastSeen: Long = 0,
  val lastActiveAt: Long = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val isVerified: Boolean = false,
  val website: String = "",
  val socialLinks: Map<String, String> = emptyMap()
)

data class MessageReaction(
  val emoji: String = "",
  val userIds: List<String> = emptyList()
)

data class Message(
  val id: String = "",
  val conversationId: String = "",
  val senderId: String = "",
  val senderName: String = "",
  val text: String = "",
  val type: MessageType = MessageType.TEXT,
  val mediaUrl: String = "",
  val voiceDurationSeconds: Int = 0,
  val waveform: List<Float> = emptyList(),
  val createdAt: Long = System.currentTimeMillis(),
  val status: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
  val replyToId: String? = null,
  val replyToText: String? = null,
  val replyToSender: String? = null,
  val reactions: List<MessageReaction> = emptyList(),
  val isEdited: Boolean = false,
  val isDeleted: Boolean = false,
  val isPinned: Boolean = false,
  val isStarred: Boolean = false,
  val expiresAt: Long? = null
)

data class Conversation(
  val id: String = "",
  val participantIds: List<String> = emptyList(),
  val otherUser: User = User(),
  val lastMessageText: String = "",
  val lastMessageTime: Long = System.currentTimeMillis(),
  val lastMessageSenderId: String = "",
  val unreadCount: Int = 0,
  val isPinned: Boolean = false,
  val isMuted: Boolean = false,
  val isOnline: Boolean = false,
  val isTyping: Boolean = false,
  val typingUntil: Long = 0,
  val isArchived: Boolean = false,
  val disappearingSeconds: Long = 0L,
  val wallpaperIndex: Int = 0
)

data class AppearanceSettings(
  val isDarkMode: Boolean = false,
  val glassIntensity: Float = 0.85f,
  val blurAlpha: Float = 0.70f,
  val cornerRadiusDp: Float = 32f,
  val borderStrength: Float = 0.70f,
  val accentColorHex: String = "#176BFF",
  val isReducedMotion: Boolean = false,
  val glassStyle: String = "Regular"
)

data class NotificationSettings(
  val messages: Boolean = true,
  val vibration: Boolean = true,
  val showPreview: Boolean = true
)

data class PrivacySettings(
  val lastSeenVisibility: String = "Everyone",
  val onlineVisibility: String = "Everyone",
  val profilePhotoVisibility: String = "Everyone",
  val readReceipts: Boolean = true
)
