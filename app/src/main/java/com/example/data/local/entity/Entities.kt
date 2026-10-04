package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
  @PrimaryKey val uid: String,
  val displayName: String,
  val username: String,
  val email: String,
  val phoneNumber: String,
  val photoUrl: String,
  val bio: String,
  val isOnline: Boolean,
  val lastSeen: Long
)

@Entity(tableName = "conversations")
data class ConversationEntity(
  @PrimaryKey val id: String,
  val otherUserId: String,
  val otherUserName: String,
  val otherUserPhoto: String,
  val lastMessageText: String,
  val lastMessageTime: Long,
  val lastMessageSenderId: String,
  val unreadCount: Int,
  val isPinned: Boolean,
  val isMuted: Boolean,
  val isArchived: Boolean,
  val disappearingSeconds: Long,
  val wallpaperIndex: Int
)

@Entity(
  tableName = "messages",
  indices = [Index(value = ["conversationId"]), Index(value = ["createdAt"])]
)
data class MessageEntity(
  @PrimaryKey val id: String,
  val conversationId: String,
  val senderId: String,
  val senderName: String,
  val text: String,
  val type: String,
  val mediaUrl: String,
  val voiceDurationSeconds: Int,
  val waveformCsv: String,
  val createdAt: Long,
  val status: String,
  val replyToId: String?,
  val replyToText: String?,
  val replyToSender: String?,
  val reactionsJson: String,
  val isEdited: Boolean,
  val isDeleted: Boolean,
  val isPinned: Boolean,
  val isStarred: Boolean,
  val expiresAt: Long?
)


@Entity(
  tableName = "message_outbox",
  indices = [
    Index(value = ["accountId"]),
    Index(value = ["conversationId"]),
    Index(value = ["messageId"], unique = true)
  ]
)
data class OutboxMessageEntity(
  @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
  val messageId: String,
  val accountId: String,
  val conversationId: String,
  val payloadJson: String,
  val createdAt: Long,
  val state: String = "PENDING",
  val attemptCount: Int = 0,
  val lastError: String = ""
)
