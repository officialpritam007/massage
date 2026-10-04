package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.OutboxMessageEntity
import com.example.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
  @Query("SELECT * FROM users")
  fun getAllUsers(): Flow<List<UserEntity>>

  @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
  suspend fun getUserById(uid: String): UserEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUser(user: UserEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUsers(users: List<UserEntity>)
}

@Dao
interface ConversationDao {
  @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTime DESC")
  fun getAllConversations(): Flow<List<ConversationEntity>>

  @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTime DESC")
  suspend fun getAllConversationsOnce(): List<ConversationEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertConversation(conversation: ConversationEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertConversations(conversations: List<ConversationEntity>)

  @Query("DELETE FROM conversations")
  suspend fun clearConversations()

  @Transaction
  suspend fun replaceConversations(conversations: List<ConversationEntity>) {
    clearConversations()
    if (conversations.isNotEmpty()) insertConversations(conversations)
  }

  @Query("DELETE FROM conversations WHERE id = :id")
  suspend fun deleteConversation(id: String)
}

@Dao
interface MessageDao {
  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
  fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
  suspend fun getMessagesForConversationOnce(conversationId: String): List<MessageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: MessageEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessages(messages: List<MessageEntity>)

  @Query("DELETE FROM messages WHERE conversationId = :conversationId")
  suspend fun clearConversation(conversationId: String)

  @Transaction
  suspend fun replaceConversation(conversationId: String, messages: List<MessageEntity>) {
    clearConversation(conversationId)
    if (messages.isNotEmpty()) insertMessages(messages)
  }

  @Query("DELETE FROM messages WHERE id = :id")
  suspend fun deleteMessage(id: String)
}


@Dao
interface OutboxDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun enqueue(item: OutboxMessageEntity): Long

  @Query("SELECT * FROM message_outbox WHERE accountId = :accountId ORDER BY queueId ASC")
  suspend fun getForAccount(accountId: String): List<OutboxMessageEntity>

  @Query("UPDATE message_outbox SET state = :state, attemptCount = attemptCount + 1, lastError = :lastError WHERE messageId = :messageId")
  suspend fun updateState(messageId: String, state: String, lastError: String = "")

  @Query("UPDATE message_outbox SET state = 'PENDING', lastError = '' WHERE messageId = :messageId")
  suspend fun markPending(messageId: String)

  @Query("DELETE FROM message_outbox WHERE messageId = :messageId")
  suspend fun deleteByMessageId(messageId: String)

  @Query("DELETE FROM message_outbox WHERE conversationId = :conversationId")
  suspend fun deleteForConversation(conversationId: String)

  @Query("DELETE FROM message_outbox WHERE accountId = :accountId")
  suspend fun clearAccount(accountId: String)
}
