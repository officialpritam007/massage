package com.example.data.local.entity

import androidx.room.Entity
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
  val unreadCount: Int,
  val isPinned: Boolean,
  val isMuted: Boolean
)

@Entity(tableName = "messages")
data class MessageEntity(
  @PrimaryKey val id: String,
  val conversationId: String,
  val senderId: String,
  val senderName: String,
  val text: String,
  val type: String,
  val mediaUrl: String,
  val voiceDurationSeconds: Int,
  val createdAt: Long,
  val status: String,
  val isEdited: Boolean,
  val isPinned: Boolean
)
