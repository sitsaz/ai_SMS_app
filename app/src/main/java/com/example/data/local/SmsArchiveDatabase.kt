package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SmsMessage::class,
        SmsMessageFts::class,
        NotificationItem::class,
        NotificationItemFts::class,
        AiTopicEntity::class,
        AiChatMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SmsArchiveDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun aiTopicDao(): AiTopicDao

    companion object {
        @Volatile
        private var INSTANCE: SmsArchiveDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_topics ADD COLUMN contextMode TEXT NOT NULL DEFAULT 'COMPACT_TEXT'")
                db.execSQL("ALTER TABLE ai_chat_messages ADD COLUMN structuredJson TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): SmsArchiveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmsArchiveDatabase::class.java,
                    "ai_sms_manager.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
