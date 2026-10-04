package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.OutboxMessageEntity
import com.example.data.local.entity.UserEntity

@Database(
  entities = [UserEntity::class, ConversationEntity::class, MessageEntity::class, OutboxMessageEntity::class],
  version = 3,
  exportSchema = false
)
abstract class LiquidChatDatabase : RoomDatabase() {
  abstract fun userDao(): UserDao
  abstract fun conversationDao(): ConversationDao
  abstract fun messageDao(): MessageDao
  abstract fun outboxDao(): OutboxDao

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

    private val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS message_outbox (
            queueId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            messageId TEXT NOT NULL,
            accountId TEXT NOT NULL,
            conversationId TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            state TEXT NOT NULL,
            attemptCount INTEGER NOT NULL,
            lastError TEXT NOT NULL
          )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_outbox_accountId ON message_outbox(accountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_outbox_conversationId ON message_outbox(conversationId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_message_outbox_messageId ON message_outbox(messageId)")
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).fallbackToDestructiveMigration().build()
        INSTANCE = instance
        instance
      }
    }
  }
}
