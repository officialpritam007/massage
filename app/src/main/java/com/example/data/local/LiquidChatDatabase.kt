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
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
  entities = [UserEntity::class, ConversationEntity::class, MessageEntity::class],
  version = 2,
  exportSchema = false
)
abstract class LiquidChatDatabase : RoomDatabase() {
  abstract fun userDao(): UserDao
  abstract fun conversationDao(): ConversationDao
  abstract fun messageDao(): MessageDao

  companion object {
    @Volatile
    private var INSTANCE: LiquidChatDatabase? = null

    const val DATABASE_NAME = "liquid_chat_secure_v2.db"

    fun getDatabase(context: Context): LiquidChatDatabase {
      return INSTANCE ?: synchronized(this) {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseKeyManager.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)
        val instance = Room.databaseBuilder(
          context.applicationContext,
          LiquidChatDatabase::class.java,
          context.getDatabasePath(DATABASE_NAME).absolutePath
        )
          .openHelperFactory(factory)
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
