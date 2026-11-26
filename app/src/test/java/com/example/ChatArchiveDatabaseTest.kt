package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.archive.ChatArchiveRepository
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiTopicEntity
import com.example.data.local.SmsArchiveDatabase
import com.example.data.local.SmsMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatArchiveDatabaseTest {

    private lateinit var db: SmsArchiveDatabase
    private lateinit var repository: ChatArchiveRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmsArchiveDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = ChatArchiveRepository(
            smsDao = db.smsDao(),
            smsThreadDao = db.smsThreadDao(),
            aiTopicDao = db.aiTopicDao()
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `persisting incoming sms updates thread summary and retrieves incoming messages`() = runTest {
        val threadId = 42L
        repository.persistSmsAndUpdateThread(
            SmsMessage(
                systemSmsId = 1001L,
                threadId = threadId,
                sender = "+15551234567",
                address = "+15551234567",
                normalizedAddress = "5551234567",
                contactName = "Alice",
                body = "Your verification code is 849201",
                timestamp = 1_700_000_100_000L,
                isOutgoing = false,
                isRead = false
            )
        )
        repository.persistSmsAndUpdateThread(
            SmsMessage(
                systemSmsId = 1002L,
                threadId = threadId,
                sender = "Me",
                address = "+15551234567",
                normalizedAddress = "5551234567",
                contactName = "Alice",
                body = "Thanks, received!",
                timestamp = 1_700_000_200_000L,
                isOutgoing = true,
                isRead = true
            )
        )

        val threadWithMessages = repository.observeThreadWithMessages(threadId).first()
        assertNotNull(threadWithMessages)
        assertEquals("Alice", threadWithMessages!!.thread.contactName)
        assertEquals(2, threadWithMessages.thread.messageCount)
        assertEquals(1, threadWithMessages.thread.unreadCount)
        assertEquals(2, threadWithMessages.messages.size)

        val incomingOnly = repository.observeIncomingSmsForThread(threadId).first()
        assertEquals(1, incomingOnly.size)
        assertEquals("Your verification code is 849201", incomingOnly.first().body)
    }

    @Test
    fun `persisting ai chat topic with xml content links to sms thread archive`() = runTest {
        val threadId = 42L
        repository.persistSmsAndUpdateThread(
            SmsMessage(
                systemSmsId = 2001L,
                threadId = threadId,
                sender = "BankAlert",
                address = "BankAlert",
                normalizedAddress = "bankalert",
                contactName = "Bank Alert",
                body = "Deposit of $250.00 completed.",
                timestamp = 1_700_000_000_000L,
                isOutgoing = false,
                isRead = false
            )
        )

        val topicId = repository.insertTopic(
            AiTopicEntity(
                title = "Bank summary",
                createdAt = 1_700_000_500_000L,
                updatedAt = 1_700_000_500_000L,
                selectedThreadId = threadId
            )
        )

        val sampleXml = "<ai_query_request><query>Summarize deposits</query><messages><sms id=\"1\">Deposit of $250.00 completed.</sms></messages></ai_query_request>"
        repository.insertChatMessage(
            AiChatMessageEntity(
                topicId = topicId,
                role = "USER",
                text = "Summarize deposits",
                xmlSummaryLabel = "XML 1 SMS",
                xmlContent = sampleXml,
                timestamp = 1_700_000_501_000L
            )
        )
        repository.insertChatMessage(
            AiChatMessageEntity(
                topicId = topicId,
                role = "ASSISTANT",
                text = "You received one deposit of **$250.00**.",
                matchedRefsCsv = "S1",
                timestamp = 1_700_000_502_000L
            )
        )

        val xmlLogs = repository.observeGeneratedXmlLogs().first()
        assertEquals(1, xmlLogs.size)
        assertEquals(sampleXml, xmlLogs.first().xmlContent)

        val threadAiHistory = repository.observeAiHistoryForSmsThread(threadId).first()
        assertEquals(1, threadAiHistory.size)
        assertEquals(2, threadAiHistory.first().messages.size)

        val combined = repository.observeThreadsWithAiArchive().first()
        assertEquals(1, combined.size)
        val archive = combined.first()
        assertEquals(threadId, archive.thread.threadId)
        assertEquals(1, archive.incomingMessages.size)
        assertEquals(1, archive.aiSessions.size)
        assertEquals(1, archive.xmlChatLogs.size)
        assertTrue(archive.xmlChatLogs.first().xmlContent.contains("<ai_query_request>"))

        val singleArchive = repository.observeThreadWithAiArchive(threadId).first()
        assertNotNull(singleArchive)
        assertEquals(1, singleArchive!!.aiSessions.size)
    }
}
