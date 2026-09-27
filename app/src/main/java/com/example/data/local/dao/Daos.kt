package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
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

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertConversation(conversation: ConversationEntity)

  @Query("DELETE FROM conversations WHERE id = :id")
  suspend fun deleteConversation(id: String)
}

@Dao
interface MessageDao {
  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
  fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: MessageEntity)

  @Query("DELETE FROM messages WHERE id = :id")
  suspend fun deleteMessage(id: String)
}
