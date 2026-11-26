package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

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
    SMART_AUTO,
    COMPACT_TEXT,
    KEYWORD_PREFILTER,
    INLINE_XML
}

enum class BackupSyncMode {
    MANUAL,
    AUTOMATIC
}

enum class AiChatContextSource {
    SELECTED_SMS_THREAD,
    NOTIFICATION_HISTORY,
    COMBINED_ARCHIVE
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
        Index(value = ["isPinned"]),
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
    val isRead: Boolean = true,
    val isPinned: Boolean = false,
    val pinnedAt: Long = 0L
)

@Entity(tableName = "pinned_threads")
data class PinnedThreadEntity(
    @PrimaryKey val threadId: Long,
    val pinnedAt: Long
)

@Entity(
    tableName = "sms_threads",
    indices = [
        Index(value = ["normalizedAddress"]),
        Index(value = ["lastMessageTimestamp"]),
        Index(value = ["isPinned"])
    ]
)
data class SmsThreadEntity(
    @PrimaryKey val threadId: Long,
    val address: String,
    val normalizedAddress: String,
    val contactName: String? = null,
    val snippet: String = "",
    val lastMessageTimestamp: Long = 0L,
    val isLastOutgoing: Boolean = false,
    val lastDeliveryStatus: String = DeliveryStatus.NONE.name,
    val unreadCount: Int = 0,
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
    val pinnedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

data class SmsThreadWithMessages(
    @Embedded val thread: SmsThreadEntity,
    @Relation(
        parentColumn = "threadId",
        entityColumn = "threadId"
    )
    val messages: List<SmsMessage>
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
    val unreadCount: Int,
    val isPinned: Boolean = false,
    val pinnedAt: Long = 0L
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
    val extractedUrls: String = "",
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
    val contextMode: String = AiContextMode.COMPACT_TEXT.name,
    val contextSource: String = AiChatContextSource.COMBINED_ARCHIVE.name,
    val selectedThreadId: Long? = null,
    val selectedAppPackage: String? = null,
    val originScreen: String = "TOPICS"
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

@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["query"])
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val query: String,
    val origin: String = "UNIFIED",
    val sourceFilter: String = SearchSourceFilter.ALL.name,
    val directionFilter: String = SearchDirectionFilter.ALL.name,
    val sortOrder: String = SearchSortOrder.NEWEST_FIRST.name,
    val dateStartMs: Long = 0L,
    val dateEndMs: Long = 0L,
    val resultCount: Int = 0,
    val matchedRefsCsv: String = "",
    val resultsSnapshotJson: String = "",
    val timestamp: Long
)

data class AiTopicWithMessages(
    @Embedded val topic: AiTopicEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "topicId"
    )
    val messages: List<AiChatMessageEntity>
)
