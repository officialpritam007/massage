package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.UserEntity

@Database(
  entities = [UserEntity::class, ConversationEntity::class, MessageEntity::class],
  version = 1,
  exportSchema = false
)
abstract class LiquidChatDatabase : RoomDatabase() {
  abstract fun userDao(): UserDao
  abstract fun conversationDao(): ConversationDao
  abstract fun messageDao(): MessageDao

  companion object {
    @Volatile
    private var INSTANCE: LiquidChatDatabase? = null

    fun clearForLogout(context: Context) = synchronized(this) {
      INSTANCE?.clearAllTables()
      INSTANCE?.close()
      INSTANCE = null
      context.deleteDatabase("liquid_chat_db")
    }

    fun getDatabase(context: Context): LiquidChatDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          LiquidChatDatabase::class.java,
          "liquid_chat_db"
        ).fallbackToDestructiveMigration().build()
        INSTANCE = instance
        instance
      }
    }
  }
}
