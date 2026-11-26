package com.example.data.archive

import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiTopicDao
import com.example.data.local.AiTopicEntity
import com.example.data.local.AiTopicWithMessages
import com.example.data.local.SmsDao
import com.example.data.local.SmsMessage
import com.example.data.local.SmsThreadDao
import com.example.data.local.SmsThreadEntity
import com.example.data.local.SmsThreadWithMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Combined domain model pairing a persisted SMS thread and its incoming/outgoing
 * message archive with any historical AI chat sessions and generated XML payloads
 * linked to that thread.
 */
data class ThreadWithAiArchive(
    val thread: SmsThreadEntity,
    val messages: List<SmsMessage>,
    val incomingMessages: List<SmsMessage>,
    val aiSessions: List<AiTopicWithMessages>,
    val xmlChatLogs: List<AiChatMessageEntity>
)

/**
 * Repository managing retrieval and persistence of historical SMS threads,
 * incoming/outgoing SMS messages, and AI chat logs including generated XML payloads.
 */
class ChatArchiveRepository(
    private val smsDao: SmsDao,
    private val smsThreadDao: SmsThreadDao,
    private val aiTopicDao: AiTopicDao
) {

    // -------------------------------------------------------------------------
    // SMS Threads & Message Archives
    // -------------------------------------------------------------------------

    fun observeSmsThreads(): Flow<List<SmsThreadEntity>> =
        smsThreadDao.observeThreads()

    fun observeThreadsWithMessages(): Flow<List<SmsThreadWithMessages>> =
        smsThreadDao.observeThreadsWithMessages().map { list ->
            list.map { item ->
                item.copy(messages = item.messages.sortedWith(compareBy({ it.timestamp }, { it.id })))
            }
        }

    fun observeThreadWithMessages(threadId: Long): Flow<SmsThreadWithMessages?> =
        smsThreadDao.observeThreadWithMessages(threadId).map { item ->
            item?.copy(messages = item.messages.sortedWith(compareBy({ it.timestamp }, { it.id })))
        }

    fun observeIncomingSmsForThread(threadId: Long): Flow<List<SmsMessage>> =
        smsDao.observeIncomingThreadMessages(threadId)

    suspend fun getThreadById(threadId: Long): SmsThreadEntity? = withContext(Dispatchers.IO) {
        smsThreadDao.getThreadById(threadId)
    }

    suspend fun persistSmsAndUpdateThread(message: SmsMessage): Long = withContext(Dispatchers.IO) {
        val id = smsDao.insertMessage(message)
        refreshThreadSummary(message.threadId)
        id
    }

    suspend fun refreshThreadSummary(threadId: Long) = withContext(Dispatchers.IO) {
        val msgs = smsDao.getThreadMessagesOnce(threadId)
        if (msgs.isEmpty()) {
            smsThreadDao.deleteThread(threadId)
            return@withContext
        }
        val latest = msgs.maxWithOrNull(compareBy<SmsMessage> { it.timestamp }.thenBy { it.id }) ?: return@withContext
        val existingThread = smsThreadDao.getThreadById(threadId)
        val pinnedInTable = smsDao.isThreadPinned(threadId) > 0
        val isPinned = existingThread?.isPinned == true || pinnedInTable
        val pinnedAt = existingThread?.pinnedAt ?: 0L
        val unreadCount = msgs.count { !it.isRead && !it.isOutgoing }
        val resolvedName = msgs.lastOrNull { !it.contactName.isNullOrBlank() }?.contactName
            ?: latest.contactName

        smsThreadDao.upsertThread(
            SmsThreadEntity(
                threadId = threadId,
                address = latest.address,
                normalizedAddress = latest.normalizedAddress,
                contactName = resolvedName,
                snippet = latest.body,
                lastMessageTimestamp = latest.timestamp,
                isLastOutgoing = latest.isOutgoing,
                lastDeliveryStatus = latest.deliveryStatus,
                unreadCount = unreadCount,
                messageCount = msgs.size,
                isPinned = isPinned,
                pinnedAt = pinnedAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun syncAllThreadSummaries() = withContext(Dispatchers.IO) {
        val threadIds = smsDao.getAllThreadIds()
        for (tid in threadIds) {
            refreshThreadSummary(tid)
        }
    }

    // -------------------------------------------------------------------------
    // AI Chat History & Generated XML Content
    // -------------------------------------------------------------------------

    fun observeTopics(): Flow<List<AiTopicEntity>> =
        aiTopicDao.observeTopics()

    fun observeTopicsWithMessages(): Flow<List<AiTopicWithMessages>> =
        aiTopicDao.observeTopicsWithMessages().map { list ->
            list.map { item ->
                item.copy(messages = item.messages.sortedWith(compareBy({ it.timestamp }, { it.id })))
            }
        }

    fun observeTopic(topicId: Long): Flow<AiTopicEntity?> =
        aiTopicDao.observeTopic(topicId)

    fun observeTopicWithMessages(topicId: Long): Flow<AiTopicWithMessages?> =
        aiTopicDao.observeTopicWithMessages(topicId).map { item ->
            item?.copy(messages = item.messages.sortedWith(compareBy({ it.timestamp }, { it.id })))
        }

    fun observeMessagesForTopic(topicId: Long): Flow<List<AiChatMessageEntity>> =
        aiTopicDao.observeMessagesForTopic(topicId)

    fun observeAllChatMessages(): Flow<List<AiChatMessageEntity>> =
        aiTopicDao.observeAllChatMessages()

    fun observeGeneratedXmlLogs(): Flow<List<AiChatMessageEntity>> =
        aiTopicDao.observeXmlChatLogs()

    fun observeAiHistoryForSmsThread(threadId: Long): Flow<List<AiTopicWithMessages>> =
        aiTopicDao.observeTopicsForSmsThread(threadId).map { list ->
            list.map { item ->
                item.copy(messages = item.messages.sortedWith(compareBy({ it.timestamp }, { it.id })))
            }
        }

    suspend fun getTopicById(topicId: Long): AiTopicEntity? = withContext(Dispatchers.IO) {
        aiTopicDao.getTopicById(topicId)
    }

    suspend fun getMessagesForTopicOnce(topicId: Long): List<AiChatMessageEntity> = withContext(Dispatchers.IO) {
        aiTopicDao.getMessagesForTopicOnce(topicId)
    }

    suspend fun insertTopic(topic: AiTopicEntity): Long = withContext(Dispatchers.IO) {
        aiTopicDao.insertTopic(topic)
    }

    suspend fun updateTopic(topic: AiTopicEntity) = withContext(Dispatchers.IO) {
        aiTopicDao.updateTopic(topic)
    }

    suspend fun deleteTopic(topicId: Long) = withContext(Dispatchers.IO) {
        aiTopicDao.deleteTopic(topicId)
    }

    suspend fun insertChatMessage(message: AiChatMessageEntity): Long = withContext(Dispatchers.IO) {
        aiTopicDao.insertChatMessage(message)
    }

    suspend fun deleteChatMessage(messageId: Long) = withContext(Dispatchers.IO) {
        aiTopicDao.deleteChatMessage(messageId)
    }

    // -------------------------------------------------------------------------
    // Unified Retrieval: AI Interactions Alongside SMS Thread Archives
    // -------------------------------------------------------------------------

    fun observeThreadsWithAiArchive(): Flow<List<ThreadWithAiArchive>> =
        combine(
            observeThreadsWithMessages(),
            observeTopicsWithMessages()
        ) { threadsWithMsgs, topicsWithMsgs ->
            val aiByThread = topicsWithMsgs
                .filter { it.topic.selectedThreadId != null }
                .groupBy { it.topic.selectedThreadId!! }

            threadsWithMsgs.map { twm ->
                val linkedAi = aiByThread[twm.thread.threadId].orEmpty()
                val xmlLogs = linkedAi
                    .flatMap { it.messages }
                    .filter { it.xmlContent.isNotBlank() }
                    .sortedByDescending { it.timestamp }
                ThreadWithAiArchive(
                    thread = twm.thread,
                    messages = twm.messages,
                    incomingMessages = twm.messages.filter { !it.isOutgoing },
                    aiSessions = linkedAi,
                    xmlChatLogs = xmlLogs
                )
            }
        }

    fun observeThreadWithAiArchive(threadId: Long): Flow<ThreadWithAiArchive?> =
        combine(
            observeThreadWithMessages(threadId),
            observeAiHistoryForSmsThread(threadId)
        ) { twm, linkedAi ->
            if (twm == null) {
                null
            } else {
                val xmlLogs = linkedAi
                    .flatMap { it.messages }
                    .filter { it.xmlContent.isNotBlank() }
                    .sortedByDescending { it.timestamp }
                ThreadWithAiArchive(
                    thread = twm.thread,
                    messages = twm.messages,
                    incomingMessages = twm.messages.filter { !it.isOutgoing },
                    aiSessions = linkedAi,
                    xmlChatLogs = xmlLogs
                )
            }
        }
}
