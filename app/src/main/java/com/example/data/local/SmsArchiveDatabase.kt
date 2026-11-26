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
        SearchHistoryEntity::class,
        SmsThreadEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class SmsArchiveDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun smsThreadDao(): SmsThreadDao
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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sms_threads (
                        threadId INTEGER NOT NULL PRIMARY KEY,
                        address TEXT NOT NULL,
                        normalizedAddress TEXT NOT NULL,
                        contactName TEXT DEFAULT NULL,
                        snippet TEXT NOT NULL DEFAULT '',
                        lastMessageTimestamp INTEGER NOT NULL DEFAULT 0,
                        isLastOutgoing INTEGER NOT NULL DEFAULT 0,
                        lastDeliveryStatus TEXT NOT NULL DEFAULT 'NONE',
                        unreadCount INTEGER NOT NULL DEFAULT 0,
                        messageCount INTEGER NOT NULL DEFAULT 0,
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        pinnedAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_threads_normalizedAddress ON sms_threads(normalizedAddress)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_threads_lastMessageTimestamp ON sms_threads(lastMessageTimestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_threads_isPinned ON sms_threads(isPinned)")
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO sms_threads (
                        threadId, address, normalizedAddress, contactName, snippet,
                        lastMessageTimestamp, isLastOutgoing, lastDeliveryStatus,
                        unreadCount, messageCount, isPinned, pinnedAt, updatedAt
                    )
                    SELECT s.threadId, s.address, s.normalizedAddress, s.contactName, s.body,
                           s.timestamp, s.isOutgoing, s.deliveryStatus,
                           (SELECT COUNT(*) FROM sms_messages u WHERE u.threadId = s.threadId AND u.isRead = 0 AND u.isOutgoing = 0),
                           (SELECT COUNT(*) FROM sms_messages c WHERE c.threadId = s.threadId),
                           CASE WHEN pt.threadId IS NOT NULL THEN 1 ELSE 0 END,
                           COALESCE(pt.pinnedAt, 0),
                           s.timestamp
                    FROM sms_messages s
                    INNER JOIN (
                        SELECT threadId, MAX(timestamp) AS maxTs
                        FROM sms_messages
                        GROUP BY threadId
                    ) latest ON s.threadId = latest.threadId AND s.timestamp = latest.maxTs
                    LEFT JOIN pinned_threads pt ON s.threadId = pt.threadId
                    GROUP BY s.threadId
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): SmsArchiveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmsArchiveDatabase::class.java,
                    "ai_sms_manager.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
