package com.example.data.model

enum class MessageType {
  TEXT, IMAGE, VIDEO, AUDIO, VOICE, FILE, LOCATION
}

enum class MessageDeliveryStatus {
  SENDING, SENT, DELIVERED, READ
}

data class User(
  val uid: String = "",
  val displayName: String = "",
  val username: String = "",
  val email: String = "",
  val phoneNumber: String = "",
  val photoUrl: String = "",
  val bio: String = "Fluid connections in real-time.",
  val isOnline: Boolean = false,
  val lastSeen: Long = System.currentTimeMillis(),
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
  val createdAt: Long = System.currentTimeMillis(),
  val status: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
  val replyToId: String? = null,
  val replyToText: String? = null,
  val replyToSender: String? = null,
  val reactions: List<MessageReaction> = emptyList(),
  val isEdited: Boolean = false,
  val isDeleted: Boolean = false,
  val isPinned: Boolean = false
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
  val isArchived: Boolean = false
)

data class Group(
  val id: String,
  val name: String,
  val description: String = "",
  val photoUrl: String = "",
  val adminIds: List<String>,
  val members: List<User>,
  val createdAt: Long = System.currentTimeMillis(),
  val lastMessageText: String = "",
  val lastMessageTime: Long = System.currentTimeMillis(),
  val lastMessageSender: String = "",
  val unreadCount: Int = 0,
  val onlyAdminsCanPost: Boolean = false,
  val isMuted: Boolean = false,
  val isPinned: Boolean = false
)

enum class StatusType {
  TEXT, IMAGE, VIDEO
}

data class UserStatus(
  val id: String,
  val userId: String,
  val userName: String,
  val userPhotoUrl: String,
  val type: StatusType = StatusType.TEXT,
  val content: String,
  val backgroundGradientIndex: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val expiresAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000,
  val viewerNames: List<String> = emptyList(),
  val isViewedByMe: Boolean = false
)

enum class CallType {
  AUDIO, VIDEO
}

enum class CallStatus {
  INCOMING, OUTGOING, MISSED, COMPLETED
}

data class CallRecord(
  val id: String,
  val otherUser: User,
  val type: CallType = CallType.AUDIO,
  val status: CallStatus = CallStatus.COMPLETED,
  val timestamp: Long = System.currentTimeMillis(),
  val durationSeconds: Int = 0
)

data class ActiveCallState(
  val callId: String,
  val user: User,
  val type: CallType,
  val isOutgoing: Boolean,
  val isConnected: Boolean = false,
  val isMuted: Boolean = false,
  val isCameraOn: Boolean = true,
  val isSpeakerOn: Boolean = true,
  val durationSeconds: Int = 0
)

data class AppearanceSettings(
  val isDarkMode: Boolean = false,
  val glassIntensity: Float = 0.85f,
  val blurAlpha: Float = 0.70f,
  val accentColorHex: String = "#176BFF",
  val isReducedMotion: Boolean = false
)

data class NotificationSettings(
  val messages: Boolean = true,
  val calls: Boolean = true,
  val status: Boolean = true,
  val vibration: Boolean = true
)

data class PrivacySettings(
  val lastSeenVisibility: String = "Everyone",
  val onlineVisibility: String = "Everyone",
  val profilePhotoVisibility: String = "Everyone",
  val readReceipts: Boolean = true,
  val statusVisibility: String = "Contacts Only",
  val whoCanAddToGroups: String = "Everyone"
)
