package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DeliveryStatus {
    NONE,
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

enum class ArchiveRangePreset {
    TODAY,
    TWO_DAYS,
    THREE_DAYS,
    ONE_WEEK,
    TWO_WEEKS,
    ONE_MONTH,
    ONE_YEAR,
    CUSTOM
}

enum class AiProviderType {
    OPENAI_COMPATIBLE,
    GEMINI_REST,
    CUSTOM
}

enum class AiContextMode {
    COMPACT_TEXT,
    KEYWORD_PREFILTER,
    INLINE_XML
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class SearchSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
    SENDER_ASC
}

enum class SearchSourceFilter {
    ALL,
    SMS_ONLY,
    NOTIFICATIONS_ONLY
}

enum class SearchDirectionFilter {
    ALL,
    INCOMING,
    OUTGOING
}

@Entity(
    tableName = "sms_messages",
    indices = [
        Index(value = ["threadId"]),
        Index(value = ["sender"]),
        Index(value = ["address"]),
        Index(value = ["normalizedAddress"]),
        Index(value = ["contactName"]),
        Index(value = ["body"]),
        Index(value = ["timestamp"]),
        Index(value = ["systemSmsId"], unique = true)
    ]
)
data class SmsMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val systemSmsId: Long? = null,
    val threadId: Long,
    val sender: String = "",
    val address: String,
    val normalizedAddress: String,
    val contactName: String? = null,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val deliveryStatus: String = DeliveryStatus.NONE.name,
    val isRead: Boolean = true
)

@Fts4(contentEntity = SmsMessage::class)
@Entity(tableName = "sms_messages_fts")
data class SmsMessageFts(
    val sender: String,
    val address: String,
    val contactName: String?,
    val body: String
)

data class ConversationRow(
    val id: Long,
    val threadId: Long,
    val address: String,
    val normalizedAddress: String,
    val contactName: String?,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val deliveryStatus: String,
    val unreadCount: Int
)

@Entity(
    tableName = "notification_items",
    indices = [
        Index(value = ["threadId"]),
        Index(value = ["sender"]),
        Index(value = ["packageName"]),
        Index(value = ["appName"]),
        Index(value = ["senderOrTitle"]),
        Index(value = ["body"]),
        Index(value = ["timestamp"])
    ]
)
data class NotificationItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val notificationKey: String,
    val threadId: String = "",
    val sender: String = "",
    val packageName: String,
    val appName: String,
    val senderOrTitle: String,
    val body: String,
    val subText: String = "",
    val timestamp: Long,
    val isRead: Boolean = false
)

@Fts4(contentEntity = NotificationItem::class)
@Entity(tableName = "notification_items_fts")
data class NotificationItemFts(
    val sender: String,
    val appName: String,
    val senderOrTitle: String,
    val body: String,
    val subText: String
)

@Entity(tableName = "ai_topics")
data class AiTopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val rangePreset: String = ArchiveRangePreset.ONE_WEEK.name,
    val customStartMs: Long = 0L,
    val customEndMs: Long = 0L,
    val maxItems: Int = 150,
    val includeSms: Boolean = true,
    val includeNotifications: Boolean = true,
    val contextMode: String = AiContextMode.COMPACT_TEXT.name
)

@Entity(
    tableName = "ai_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiTopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["topicId"])]
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val topicId: Long,
    val role: String,
    val text: String,
    val xmlSummaryLabel: String = "",
    val xmlContent: String = "",
    val matchedRefsCsv: String = "",
    val structuredJson: String = "",
    val timestamp: Long,
    val isError: Boolean = false
)
