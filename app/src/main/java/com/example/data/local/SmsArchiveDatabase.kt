package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SmsMessage::class,
        SmsMessageFts::class,
        NotificationItem::class,
        NotificationItemFts::class,
        AiTopicEntity::class,
        AiChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SmsArchiveDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun aiTopicDao(): AiTopicDao

    companion object {
        @Volatile
        private var INSTANCE: SmsArchiveDatabase? = null

        fun getInstance(context: Context): SmsArchiveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmsArchiveDatabase::class.java,
                    "ai_sms_manager.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
