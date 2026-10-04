package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.UserEntity

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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN lastMessageSenderId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE conversations ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE conversations ADD COLUMN disappearingSeconds INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE conversations ADD COLUMN wallpaperIndex INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE messages ADD COLUMN waveformCsv TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN replyToId TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN replyToText TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN replyToSender TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN reactionsJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE messages ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN expiresAt INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversationId ON messages(conversationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_createdAt ON messages(createdAt)")
      }
    }

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
        ).addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build()
        INSTANCE = instance
        instance
      }
    }
  }
}
