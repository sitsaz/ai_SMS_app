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
        PinnedThreadEntity::class,
        NotificationItem::class,
        NotificationItemFts::class,
        AiTopicEntity::class,
        AiChatMessageEntity::class,
        SearchHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class SmsArchiveDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun aiTopicDao(): AiTopicDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SmsArchiveDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_topics ADD COLUMN contextMode TEXT NOT NULL DEFAULT 'COMPACT_TEXT'")
                db.execSQL("ALTER TABLE ai_chat_messages ADD COLUMN structuredJson TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN pinnedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_messages_isPinned ON sms_messages(isPinned)")
                db.execSQL("ALTER TABLE notification_items ADD COLUMN extractedUrls TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pinned_threads (
                        threadId INTEGER NOT NULL PRIMARY KEY,
                        pinnedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_topics ADD COLUMN contextSource TEXT NOT NULL DEFAULT 'COMBINED_ARCHIVE'")
                db.execSQL("ALTER TABLE ai_topics ADD COLUMN selectedThreadId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE ai_topics ADD COLUMN selectedAppPackage TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE ai_topics ADD COLUMN originScreen TEXT NOT NULL DEFAULT 'TOPICS'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        query TEXT NOT NULL,
                        origin TEXT NOT NULL DEFAULT 'UNIFIED',
                        sourceFilter TEXT NOT NULL DEFAULT 'ALL',
                        directionFilter TEXT NOT NULL DEFAULT 'ALL',
                        sortOrder TEXT NOT NULL DEFAULT 'NEWEST_FIRST',
                        dateStartMs INTEGER NOT NULL DEFAULT 0,
                        dateEndMs INTEGER NOT NULL DEFAULT 0,
                        resultCount INTEGER NOT NULL DEFAULT 0,
                        matchedRefsCsv TEXT NOT NULL DEFAULT '',
                        resultsSnapshotJson TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_timestamp ON search_history(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_query ON search_history(query)")
            }
        }

        fun getInstance(context: Context): SmsArchiveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmsArchiveDatabase::class.java,
                    "ai_sms_manager.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
