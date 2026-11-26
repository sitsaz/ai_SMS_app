package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query(
        """
        SELECT s.id, s.threadId, s.address, s.normalizedAddress, s.contactName,
               s.body, s.timestamp, s.isOutgoing, s.deliveryStatus,
               (SELECT COUNT(*) FROM sms_messages u
                WHERE u.threadId = s.threadId AND u.isRead = 0 AND u.isOutgoing = 0) AS unreadCount
        FROM sms_messages s
        INNER JOIN (
            SELECT threadId, MAX(timestamp) AS maxTs
            FROM sms_messages
            GROUP BY threadId
        ) latest ON s.threadId = latest.threadId AND s.timestamp = latest.maxTs
        GROUP BY s.threadId
        ORDER BY s.timestamp DESC
        """
    )
    fun observeConversations(): Flow<List<ConversationRow>>

    @Query("SELECT * FROM sms_messages WHERE threadId = :threadId ORDER BY timestamp ASC, id ASC")
    fun observeThreadMessages(threadId: Long): Flow<List<SmsMessage>>

    @Query(
        """
        SELECT * FROM sms_messages
        WHERE (:query = '' OR body LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' OR IFNULL(contactName, '') LIKE '%' || :query || '%')
          AND (:startMs <= 0 OR timestamp >= :startMs)
          AND (:endMs <= 0 OR timestamp <= :endMs)
        ORDER BY timestamp DESC
        """
    )
    fun searchSms(query: String, startMs: Long, endMs: Long): Flow<List<SmsMessage>>

    @Query(
        """
        SELECT sms_messages.* FROM sms_messages
        JOIN sms_messages_fts ON sms_messages.id = sms_messages_fts.rowid
        WHERE sms_messages_fts MATCH :ftsQuery
          AND (:startMs <= 0 OR sms_messages.timestamp >= :startMs)
          AND (:endMs <= 0 OR sms_messages.timestamp <= :endMs)
        ORDER BY sms_messages.timestamp DESC
        """
    )
    fun searchSmsFts(ftsQuery: String, startMs: Long, endMs: Long): Flow<List<SmsMessage>>

    @Query(
        """
        SELECT * FROM sms_messages
        WHERE timestamp >= :startMs AND timestamp <= :endMs
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getSmsInRange(startMs: Long, endMs: Long, limit: Int): List<SmsMessage>

    @Query("SELECT COUNT(*) FROM sms_messages WHERE timestamp >= :startMs AND timestamp <= :endMs")
    suspend fun countSmsInRange(startMs: Long, endMs: Long): Int

    @Query("SELECT * FROM sms_messages WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SmsMessage>

    @Query("SELECT * FROM sms_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsMessage?

    @Query("SELECT * FROM sms_messages WHERE normalizedAddress = :normalizedAddress ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLatestByNormalizedAddress(normalizedAddress: String): SmsMessage?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: SmsMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<SmsMessage>)

    @Query("UPDATE sms_messages SET deliveryStatus = :status WHERE id = :id")
    suspend fun updateDeliveryStatus(id: Long, status: String)

    @Query("UPDATE sms_messages SET systemSmsId = :systemId WHERE id = :id")
    suspend fun updateSystemSmsId(id: Long, systemId: Long)

    @Query("UPDATE sms_messages SET isRead = 1 WHERE threadId = :threadId AND isRead = 0")
    suspend fun markThreadRead(threadId: Long)

    @Query("UPDATE sms_messages SET contactName = :contactName WHERE normalizedAddress = :normalizedAddress")
    suspend fun updateContactNameForAddress(normalizedAddress: String, contactName: String?)

    @Query("DELETE FROM sms_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM sms_messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query("SELECT COALESCE(MAX(threadId), 1000) + 1 FROM sms_messages")
    suspend fun nextSyntheticThreadId(): Long

    @Query("SELECT * FROM sms_messages ORDER BY timestamp DESC")
    suspend fun getAllForBackup(): List<SmsMessage>

    @Query("SELECT COUNT(*) FROM sms_messages WHERE normalizedAddress = :normalizedAddress AND timestamp = :timestamp AND body = :body")
    suspend fun countExactMatch(normalizedAddress: String, timestamp: Long, body: String): Int

    @Query("SELECT threadId FROM sms_messages WHERE normalizedAddress = :normalizedAddress ORDER BY timestamp DESC LIMIT 1")
    suspend fun findThreadIdByNormalizedAddress(normalizedAddress: String): Long?
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_items ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<NotificationItem>>

    @Query(
        """
        SELECT * FROM notification_items
        WHERE (:query = '' OR body LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%' OR senderOrTitle LIKE '%' || :query || '%' OR appName LIKE '%' || :query || '%' OR subText LIKE '%' || :query || '%')
          AND (:startMs <= 0 OR timestamp >= :startMs)
          AND (:endMs <= 0 OR timestamp <= :endMs)
        ORDER BY timestamp DESC
        """
    )
    fun searchNotifications(query: String, startMs: Long, endMs: Long): Flow<List<NotificationItem>>

    @Query(
        """
        SELECT notification_items.* FROM notification_items
        JOIN notification_items_fts ON notification_items.id = notification_items_fts.rowid
        WHERE notification_items_fts MATCH :ftsQuery
          AND (:startMs <= 0 OR notification_items.timestamp >= :startMs)
          AND (:endMs <= 0 OR notification_items.timestamp <= :endMs)
        ORDER BY notification_items.timestamp DESC
        """
    )
    fun searchNotificationsFts(ftsQuery: String, startMs: Long, endMs: Long): Flow<List<NotificationItem>>

    @Query(
        """
        SELECT * FROM notification_items
        WHERE timestamp >= :startMs AND timestamp <= :endMs
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getNotificationsInRange(startMs: Long, endMs: Long, limit: Int): List<NotificationItem>

    @Query("SELECT COUNT(*) FROM notification_items WHERE timestamp >= :startMs AND timestamp <= :endMs")
    suspend fun countNotificationsInRange(startMs: Long, endMs: Long): Int

    @Query("SELECT * FROM notification_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<NotificationItem>

    @Query("SELECT * FROM notification_items WHERE notificationKey = :key AND body = :body AND timestamp > :sinceMs LIMIT 1")
    suspend fun findRecentDuplicate(key: String, body: String, sinceMs: Long): NotificationItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationItem): Long

    @Query("DELETE FROM notification_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notification_items")
    suspend fun clearAll()

    @Query("SELECT * FROM notification_items ORDER BY timestamp DESC")
    suspend fun getAllForBackup(): List<NotificationItem>

    @Query("SELECT COUNT(*) FROM notification_items WHERE notificationKey = :key AND timestamp = :timestamp AND body = :body")
    suspend fun countExactMatch(key: String, timestamp: Long, body: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(notifications: List<NotificationItem>)
}

@Dao
interface AiTopicDao {
    @Query("SELECT * FROM ai_topics ORDER BY updatedAt DESC")
    fun observeTopics(): Flow<List<AiTopicEntity>>

    @Query("SELECT * FROM ai_topics WHERE id = :topicId LIMIT 1")
    suspend fun getTopicById(topicId: Long): AiTopicEntity?

    @Query("SELECT * FROM ai_topics WHERE id = :topicId LIMIT 1")
    fun observeTopic(topicId: Long): Flow<AiTopicEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: AiTopicEntity): Long

    @Update
    suspend fun updateTopic(topic: AiTopicEntity)

    @Query("DELETE FROM ai_topics WHERE id = :topicId")
    suspend fun deleteTopic(topicId: Long)

    @Query("SELECT * FROM ai_chat_messages WHERE topicId = :topicId ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForTopic(topicId: Long): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE topicId = :topicId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForTopicOnce(topicId: Long): List<AiChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: AiChatMessageEntity): Long
}
