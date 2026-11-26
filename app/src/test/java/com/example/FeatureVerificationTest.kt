package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.localization.CalendarMode
import com.example.core.localization.DateFormatter
import com.example.core.localization.DateUtils
import com.example.data.ai.AiSearchClient
import com.example.data.archive.ChatArchiveRepository
import com.example.data.archive.ConversationExportFormat
import com.example.data.archive.ConversationExportFormatter
import com.example.data.archive.ThreadWithAiArchive
import com.example.data.local.AiChatContextSource
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiContextMode
import com.example.data.local.AiTopicEntity
import com.example.data.local.AiTopicWithMessages
import com.example.data.local.DeliveryStatus
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SmsArchiveDatabase
import com.example.data.local.SmsMessage
import com.example.data.local.SmsThreadEntity
import com.example.data.search.ArchiveItemSource
import com.example.data.search.SearchResultItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeatureVerificationTest {

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
    fun extractOpenAiStyleText_handlesStandardWrappedSseResponsesAnthropicAndErrorPayloads() {
        val openAiStandard = """
            {
              "id": "chatcmpl-123",
              "choices": [
                {
                  "index": 0,
                  "message": { "role": "assistant", "content": "Standard reply OK" }
                }
              ]
            }
        """.trimIndent()
        assertEquals("Standard reply OK", AiSearchClient.extractOpenAiStyleText(openAiStandard))

        val nestedDataEnvelope = """
            {
              "status": true,
              "data": {
                "choices": [
                  {
                    "message": {
                      "content": [
                        { "type": "text", "text": "Nested gateway " },
                        { "type": "text", "text": "choices OK" }
                      ]
                    }
                  }
                ]
              }
            }
        """.trimIndent()
        assertEquals("Nested gateway choices OK", AiSearchClient.extractOpenAiStyleText(nestedDataEnvelope))

        val responsesApi = """
            {
              "id": "resp_1",
              "output_text": "Responses API reply"
            }
        """.trimIndent()
        assertEquals("Responses API reply", AiSearchClient.extractOpenAiStyleText(responsesApi))

        val anthropicPayload = """
            {
              "id": "msg_1",
              "type": "message",
              "role": "assistant",
              "content": [
                { "type": "text", "text": "Anthropic " },
                { "type": "text", "text": "format OK" }
              ]
            }
        """.trimIndent()
        assertEquals("Anthropic format OK", AiSearchClient.extractOpenAiStyleText(anthropicPayload))

        val ssePayload = """
            data: {"choices":[{"delta":{"content":"Hello "}}]}
            data: {"choices":[{"delta":{"content":"SSE world"}}]}
            data: [DONE]
        """.trimIndent()
        assertEquals("Hello SSE world", AiSearchClient.extractOpenAiStyleText(ssePayload))

        val http200ErrorPayload = """
            {
              "error": {
                "message": "Invalid quota for model",
                "type": "insufficient_quota"
              }
            }
        """.trimIndent()
        val thrown = assertThrows(IllegalStateException::class.java) {
            AiSearchClient.extractOpenAiStyleText(http200ErrorPayload)
        }
        assertTrue(thrown.message?.contains("Invalid quota for model") == true)
    }

    @Test
    fun dateFormatter_formatsJalaliGregorianAndDualTimestamps() {
        val sampleTs = DateUtils.toEpochMillis(2026, 11, 25, isPersian = false, endOfDay = false)
        val jalali = DateFormatter.formatJalali(sampleTs, usePersianDigits = false)
        val gregorian = DateFormatter.formatGregorian(sampleTs)
        val dualFa = DateFormatter.formatDualTimestamp(sampleTs, primaryMode = CalendarMode.JALALI, usePersianDigits = true)
        val dualShortEn = DateFormatter.formatDualShortDate(sampleTs, primaryMode = CalendarMode.GREGORIAN, usePersianDigits = false)

        assertTrue("Expected Jalali year 1405, got $jalali", jalali.startsWith("1405/09/04"))
        assertTrue("Expected Gregorian 2026/11/25, got $gregorian", gregorian.startsWith("2026/11/25"))
        assertTrue("Expected Persian digits in dual timestamp, got $dualFa", dualFa.contains("۱۴۰۵/۰۹/۰۴"))
        assertTrue("Expected Gregorian date in dual timestamp, got $dualFa", dualFa.contains("2026/11/25"))
        assertTrue("Expected short dual format, got $dualShortEn", dualShortEn.contains("2026-11-25") && dualShortEn.contains("1405/09/04"))
    }

    @Test
    fun conversationExportFormatter_producesReadableTextAndValidJsonForChatsThreadsAndSearches() {
        val sampleTs = DateUtils.toEpochMillis(2026, 11, 25, isPersian = false, endOfDay = false)

        val topicWithMessages = AiTopicWithMessages(
            topic = AiTopicEntity(
                id = 7L,
                title = "Bank OTP Analysis",
                createdAt = sampleTs,
                updatedAt = sampleTs + 5_000L,
                contextMode = AiContextMode.SMART_AUTO.name,
                contextSource = AiChatContextSource.SELECTED_SMS_THREAD.name,
                selectedThreadId = 42L
            ),
            messages = listOf(
                AiChatMessageEntity(
                    id = 1L,
                    topicId = 7L,
                    role = "USER",
                    text = "Find latest transfer OTP",
                    xmlSummaryLabel = "XML 2 SMS",
                    xmlContent = "<ai_query_request><user_query>Find latest transfer OTP</user_query></ai_query_request>",
                    timestamp = sampleTs + 1_000L
                ),
                AiChatMessageEntity(
                    id = 2L,
                    topicId = 7L,
                    role = "ASSISTANT",
                    text = "Latest code is **4821**.",
                    matchedRefsCsv = "sms:101",
                    timestamp = sampleTs + 2_000L
                )
            )
        )

        val aiText = ConversationExportFormatter.formatAiChatTopic(
            topicWithMessages = topicWithMessages,
            format = ConversationExportFormat.TEXT,
            isPersian = false
        )
        assertTrue(aiText.contains("Bank OTP Analysis"))
        assertTrue(aiText.contains("Find latest transfer OTP"))
        assertTrue(aiText.contains("<ai_query_request>"))
        assertTrue(aiText.contains("1405/09/04"))
        assertTrue(aiText.contains("2026/11/25"))

        val aiJson = JSONObject(
            ConversationExportFormatter.formatAiChatTopic(
                topicWithMessages = topicWithMessages,
                format = ConversationExportFormat.JSON,
                isPersian = true
            )
        )
        assertEquals("ai_chat_thread", aiJson.getString("exportType"))
        assertEquals("Bank OTP Analysis", aiJson.getJSONObject("topic").getString("title"))
        assertEquals(2, aiJson.getJSONArray("messages").length())
        assertTrue(aiJson.getJSONObject("topic").getString("createdAtJalali").startsWith("1405/09/04"))
        assertTrue(aiJson.getJSONObject("topic").getString("createdAtGregorian").startsWith("2026/11/25"))

        val smsArchive = ThreadWithAiArchive(
            thread = SmsThreadEntity(
                threadId = 42L,
                address = "+989120001122",
                normalizedAddress = "9120001122",
                contactName = "Bank Mellat",
                snippet = "Transfer code 4821",
                lastMessageTimestamp = sampleTs,
                messageCount = 1,
                unreadCount = 0
            ),
            messages = listOf(
                SmsMessage(
                    id = 101L,
                    threadId = 42L,
                    sender = "Bank Mellat",
                    address = "+989120001122",
                    normalizedAddress = "9120001122",
                    contactName = "Bank Mellat",
                    body = "Transfer code 4821",
                    timestamp = sampleTs,
                    isOutgoing = false
                )
            ),
            incomingMessages = emptyList(),
            aiSessions = listOf(topicWithMessages),
            xmlChatLogs = emptyList()
        )
        val threadJson = JSONObject(
            ConversationExportFormatter.formatSmsThreadArchive(
                archive = smsArchive,
                format = ConversationExportFormat.JSON,
                isPersian = false
            )
        )
        assertEquals("sms_thread_archive", threadJson.getString("exportType"))
        assertEquals(1, threadJson.getJSONArray("smsMessages").length())
        assertEquals(1, threadJson.getJSONArray("linkedAiSessions").length())

        val searchEntry = SearchHistoryEntity(
            id = 3L,
            query = "4821",
            resultCount = 1,
            timestamp = sampleTs
        )
        val searchResult = SearchResultItem(
            refId = "sms:101",
            source = ArchiveItemSource.SMS,
            itemId = 101L,
            threadId = 42L,
            senderDisplayName = "Bank Mellat",
            senderSecondary = "+989120001122",
            body = "Transfer code 4821",
            timestamp = sampleTs,
            isOutgoing = false,
            deliveryStatus = DeliveryStatus.DELIVERED
        )
        val searchText = ConversationExportFormatter.formatSearchArchiveEntry(
            entry = searchEntry,
            results = listOf(searchResult),
            format = ConversationExportFormat.TEXT,
            isPersian = false
        )
        assertTrue(searchText.contains("SEARCH ARCHIVE: 4821"))
        assertTrue(searchText.contains("Transfer code 4821"))

        val searchJson = JSONObject(
            ConversationExportFormatter.formatSearchArchiveEntry(
                entry = searchEntry,
                results = listOf(searchResult),
                format = ConversationExportFormat.JSON,
                isPersian = false
            )
        )
        assertEquals("search_archive_entry", searchJson.getString("exportType"))
        assertEquals(1, searchJson.getJSONArray("results").length())
    }

    @Test
    fun markAllMessagesAndThreadsRead_clearsAllUnreadCounts() = runTest {
        repository.persistSmsAndUpdateThread(
            SmsMessage(
                systemSmsId = 5001L,
                threadId = 10L,
                sender = "SenderA",
                address = "+10001",
                normalizedAddress = "10001",
                body = "Unread message 1",
                timestamp = 1_700_000_001_000L,
                isOutgoing = false,
                isRead = false
            )
        )
        repository.persistSmsAndUpdateThread(
            SmsMessage(
                systemSmsId = 5002L,
                threadId = 20L,
                sender = "SenderB",
                address = "+10002",
                normalizedAddress = "10002",
                body = "Unread message 2",
                timestamp = 1_700_000_002_000L,
                isOutgoing = false,
                isRead = false
            )
        )

        val beforeRows = db.smsDao().observeConversations().first()
        assertEquals(2, beforeRows.sumOf { it.unreadCount })

        db.smsDao().markAllMessagesRead()
        db.smsThreadDao().markAllThreadsRead()

        val afterRows = db.smsDao().observeConversations().first()
        assertEquals(0, afterRows.sumOf { it.unreadCount })

        val afterThreads = db.smsThreadDao().observeThreads().first()
        assertEquals(0, afterThreads.sumOf { it.unreadCount })
        assertEquals(2, afterThreads.size)
    }
}
