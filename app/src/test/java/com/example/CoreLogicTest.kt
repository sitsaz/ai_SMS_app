package com.example

import com.example.core.links.LinkExtractor
import com.example.core.localization.AppLanguage
import com.example.core.localization.CalendarMode
import com.example.core.localization.DateUtils
import com.example.core.localization.JalaliDate
import com.example.core.localization.LocaleManager
import com.example.data.ai.AiSearchClient
import com.example.data.ai.AiXmlResponseParser
import com.example.data.ai.ContextBudgetCalculator
import com.example.data.ai.DynamicAiRetrofitProvider
import com.example.data.ai.XmlArchiveBuilder
import com.example.data.local.AiChatContextSource
import com.example.data.local.AiProviderType
import com.example.data.local.AiTopicEntity
import com.example.data.local.BackupSyncMode
import com.example.data.local.DeliveryStatus
import com.example.data.local.NotificationItem
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SmsMessage
import com.example.data.preferences.AppSettings
import com.example.data.search.ArchiveItemSource
import com.example.data.search.ArchiveSearchRepository
import com.example.data.search.SearchResultItem
import com.example.data.sms.ContactsRepository
import com.example.ui.components.MarkdownBlock
import com.example.ui.components.MarkdownParser
import com.example.ui.components.MarkdownTableAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    @Test
    fun dateUtils_jalaliGregorianRoundTrip_isAccurate() {
        val j = DateUtils.gregorianToJalali(2026, 11, 25)
        assertEquals(JalaliDate(1405, 9, 4), j)

        val g = DateUtils.jalaliToGregorian(1405, 9, 4)
        assertEquals(Triple(2026, 11, 25), g)

        val nowruz = DateUtils.gregorianToJalali(2026, 3, 21)
        assertEquals(JalaliDate(1405, 1, 1), nowruz)
        assertTrue(DateUtils.isJalaliLeapYear(1403))
        assertFalse(DateUtils.isJalaliLeapYear(1405))
        assertEquals(30, DateUtils.daysInMonth(1403, 12, isPersian = true))
        assertEquals(29, DateUtils.daysInMonth(1405, 12, isPersian = true))
    }

    @Test
    fun dateUtilsAndLocaleManager_toggleCalendarsDynamically() {
        val sampleTs = DateUtils.toEpochMillis(1405, 9, 4, isPersian = true, endOfDay = false)

        assertEquals(CalendarMode.GREGORIAN, DateUtils.calendarModeFor(isPersian = false))
        assertEquals(CalendarMode.JALALI, DateUtils.calendarModeFor(isPersian = true))
        assertTrue(DateUtils.formatDateTime(sampleTs, isPersian = false).startsWith("2026/11/25"))
        assertTrue(DateUtils.formatDateTime(sampleTs, isPersian = true).startsWith("۱۴۰۵/۰۹/۰۴"))
        assertEquals(Triple(2026, 11, 25), DateUtils.getYearMonthDay(sampleTs, isPersian = false))
        assertEquals(Triple(1405, 9, 4), DateUtils.getYearMonthDay(sampleTs, isPersian = true))

        val grgDate = DateUtils.toGregorianDate(JalaliDate(1405, 9, 4))
        val pDate = DateUtils.toPersianDate(grgDate)
        assertEquals(1405, pDate.shYear)
        assertEquals(9, pDate.shMonth)
        assertEquals(4, pDate.shDay)
        assertTrue(DateUtils.formatDualDateTime(sampleTs, isPersianPrimary = true).contains("2026/11/25"))

        DateUtils.updateCalendarMode(CalendarMode.JALALI)
        assertEquals(CalendarMode.JALALI, DateUtils.calendarModeFlow.value)
        assertTrue(DateUtils.formatDateTime(sampleTs).startsWith("۱۴۰۵/۰۹/۰۴"))
        DateUtils.updateCalendarMode(CalendarMode.GREGORIAN)
        assertEquals(CalendarMode.GREGORIAN, DateUtils.calendarModeFlow.value)
        assertTrue(DateUtils.formatDateTime(sampleTs).startsWith("2026/11/25"))

        val enManager = LocaleManager(AppLanguage.ENGLISH)
        assertFalse(enManager.isPersian)
        assertEquals("gregorian", enManager.calendarName)
        assertTrue(enManager.formatDateTime(sampleTs).startsWith("2026/11/25"))

        val faManager = LocaleManager(AppLanguage.PERSIAN)
        assertTrue(faManager.isPersian)
        assertEquals("jalali", faManager.calendarName)
        assertTrue(faManager.formatDateTime(sampleTs).startsWith("۱۴۰۵/۰۹/۰۴"))
    }

    @Test
    fun aiSearchClient_normalizesUrlsAndExtractsCredentials() {
        assertEquals(
            "https://ai.example.com/v1/chat/completions",
            AiSearchClient.buildOpenAiChatUrl("https://ai.example.com/v1/")
        )
        assertEquals(
            "https://ai.example.com/v1/chat/completions",
            AiSearchClient.buildOpenAiChatUrl("https://ai.example.com")
        )
        assertEquals(
            "https://ai.example.com/v1/models",
            AiSearchClient.buildOpenAiModelsUrl("https://ai.example.com/v1/chat/completions")
        )
        assertEquals(
            "https://ai.example.com/v1/models",
            AiSearchClient.buildOpenAiModelsUrl("https://ai.example.com/v1/")
        )
        assertTrue(AiSearchClient.isValidHttpUrl("https://ai.example.com/v1/"))
        assertFalse(AiSearchClient.isValidHttpUrl("not-a-url"))

        val (parsedUrl, parsedKey) = AiSearchClient.parseCredentialsText(
            "https://ai.example.com/v1/\n\ndummyTestToken1234567890"
        )
        assertEquals("https://ai.example.com/v1/", parsedUrl)
        assertEquals("dummyTestToken1234567890", parsedKey)
    }

    @Test
    fun aiXmlResponseParser_parsesStructuredXmlAndFallbackFormats() {
        val rawXml = """
            ```xml
            <ai_search_response>
              <summary>Found 2 bank alerts & OTPs</summary>
              <answer>Your Mellat Bank transfer succeeded on 1405/09/04.</answer>
              <matches>
                <match id="sms:42" source="sms" sender="Bank Mellat" date="1405/09/04 14:20">
                  <snippet>Deposit 500,000 IRR</snippet>
                  <reason>Matches bank transfer query</reason>
                </match>
                <match id="notif:9" source="notification" sender="Snapp">
                  <snippet>Ride receipt</snippet>
                  <reason>Related payment alert</reason>
                </match>
              </matches>
              <follow_ups>
                <question>Show total transfers this month?</question>
              </follow_ups>
            </ai_search_response>
            ```
        """.trimIndent()

        val parsed = AiXmlResponseParser.parse(rawXml)
        assertEquals("xml", parsed.responseFormat)
        assertEquals("Found 2 bank alerts & OTPs", parsed.summary)
        assertTrue(parsed.answer.contains("Mellat Bank"))
        assertEquals(listOf("sms:42", "notif:9"), parsed.matchedRefs)
        assertEquals(2, parsed.matches.size)
        assertEquals("Matches bank transfer query", parsed.matches[0].relevanceReason)
        assertEquals(listOf("Show total transfers this month?"), parsed.suggestedFollowUps)

        val roundTrip = AiXmlResponseParser.decodeFromJson(AiXmlResponseParser.encodeToJson(parsed))
        assertNotNull(roundTrip)
        assertEquals(parsed.matchedRefs, roundTrip?.matchedRefs)

        val fallback = AiXmlResponseParser.parse("Check sms:15 and notif:3 for your code.")
        assertEquals("text", fallback.responseFormat)
        assertEquals(listOf("sms:15", "notif:3"), fallback.matchedRefs)
    }

    @Test
    fun dynamicAiRetrofitProvider_initializesFromSettingsBaseUrl() {
        val provider = DynamicAiRetrofitProvider()
        val settings1 = AppSettings(
            aiProviderType = AiProviderType.OPENAI_COMPATIBLE,
            aiBaseUrl = "https://gateway.example.com/v1",
            aiApiKey = "dummy_key_alpha_123456"
        )
        val retrofit1 = provider.getRetrofit(settings1)
        assertEquals("https://gateway.example.com/v1/", retrofit1.baseUrl().toString())

        val settings2 = settings1.copy(aiBaseUrl = "https://other.example.org/api/v1/")
        val retrofit2 = provider.getRetrofit(settings2)
        assertEquals("https://other.example.org/api/v1/", retrofit2.baseUrl().toString())
    }

    @Test
    fun xmlArchiveBuilder_keywordExtractionAndScoring_supportsPersianAndEnglish() {
        val keywords = XmlArchiveBuilder.extractSearchKeywords("پیامک بانک ملت ۱۲۳۴ درباره واریز")
        assertTrue(keywords.contains("بانک"))
        assertTrue(keywords.contains("ملت"))
        assertTrue(keywords.contains("1234"))
        assertTrue(keywords.contains("واریز"))

        val ftsQuery = XmlArchiveBuilder.buildFtsOrQuery(keywords)
        assertNotNull(ftsQuery)
        assertTrue(ftsQuery!!.contains("1234*"))

        val score = XmlArchiveBuilder.scoreTextMatch(keywords, listOf("Bank Mellat", "کد پیگیری 1234 واریز شد"))
        assertTrue(score >= 6)
    }

    @Test
    fun xmlEscape_handlesSpecialCharacters() {
        val escaped = XmlArchiveBuilder.escapeXml("""OTP <1234> & "test" 'ok'""")
        assertEquals("OTP &lt;1234&gt; &amp; &quot;test&quot; &apos;ok&apos;", escaped)
    }

    @Test
    fun phoneNormalization_matchesIranAndInternationalFormats() {
        val a = ContactsRepository.normalizeNumberKey("+98 912-345-6789")
        val b = ContactsRepository.normalizeNumberKey("09123456789")
        assertEquals(a, b)
        assertEquals("9123456789", a)
    }

    @Test
    fun contextBudgetCalculator_keepsNewestMessagesWithinTightBudget() {
        val asciiTokens = ContextBudgetCalculator.estimateTokens("Hello world from SMS archive")
        val persianTokens = ContextBudgetCalculator.estimateTokens("سلام گزارش تراکنش بانک ملت")
        assertTrue(asciiTokens > 0)
        assertTrue(persianTokens > asciiTokens)

        val newestFirst = (10 downTo 1).map { idx ->
            "msg_$idx: transaction reference code ${idx * 1111} amount ${idx * 10000} IRR confirmed"
        }
        val (selected, usedTokens) = ContextBudgetCalculator.selectNewestWithinBudget(
            newestFirstItems = newestFirst,
            availableTokens = 90
        ) { it }

        assertTrue(selected.isNotEmpty())
        assertTrue(selected.size < newestFirst.size)
        assertEquals(newestFirst.first(), selected.first())
        assertTrue(usedTokens <= 90 || selected.size == 1)

        val available = ContextBudgetCalculator.availableMessageBudgetTokens(
            contextLimitTokens = 4_096,
            userQuestion = "Summarize payments",
            priorHistoryTokens = 400
        )
        assertTrue(available in 450..3_000)
    }

    @Test
    fun markdownParser_parsesTablesCodeBlocksListsAndHeadings() {
        val md = """
            # Monthly Report

            Here is a summary table:

            | Bank | Amount | Status |
            | :--- | :---: | ---: |
            | Mellat | 500,000 | Paid |
            | Melli | 120,000 | Pending |

            - First item
            - Second item

            ```xml
            <sms id="sms:1">OTP 9988</sms>
            ```
        """.trimIndent()

        val blocks = MarkdownParser.parse(md)
        assertTrue(blocks.any { it is MarkdownBlock.Heading && it.level == 1 })
        val table = blocks.filterIsInstance<MarkdownBlock.Table>().firstOrNull()
        assertNotNull(table)
        assertEquals(listOf("Bank", "Amount", "Status"), table?.headers)
        assertEquals(
            listOf(MarkdownTableAlign.LEFT, MarkdownTableAlign.CENTER, MarkdownTableAlign.RIGHT),
            table?.alignments
        )
        assertEquals(2, table?.rows?.size)
        assertTrue(blocks.any { it is MarkdownBlock.BulletList && it.items.size == 2 })
        val code = blocks.filterIsInstance<MarkdownBlock.CodeBlock>().firstOrNull()
        assertNotNull(code)
        assertEquals("xml", code?.language)
        assertTrue(code?.code?.contains("OTP 9988") == true)
    }

    @Test
    fun linkExtractor_extractsCleansAndNormalizesUrls() {
        val text = "لینک پیگیری: https://bank.example.com/pay?id=42، یا www.example.org/help."
        val extracted = LinkExtractor.extractUrlsFromTexts(text)
        assertEquals(
            listOf("https://bank.example.com/pay?id=42", "https://www.example.org/help"),
            extracted
        )

        val serialized = LinkExtractor.serializeUrls(extracted)
        val restored = LinkExtractor.parseSerializedUrls(serialized, "https://extra.example.net/")
        assertEquals(3, restored.size)
        assertTrue(restored.contains("https://extra.example.net/"))
    }

    @Test
    fun formatStructuredXmlQuery_generatesValidXmlForThreadAndNotifications() {
        val sms = SmsMessage(
            id = 12L,
            threadId = 5L,
            sender = "Bank",
            address = "+9821000",
            normalizedAddress = "9821000",
            contactName = "Mellat",
            body = "Deposit 100 & <verified>",
            timestamp = 1_764_000_000_000L,
            isOutgoing = false
        )
        val notif = NotificationItem(
            id = 8L,
            notificationKey = "notif_key_8",
            packageName = "com.example.bank",
            appName = "BankApp",
            senderOrTitle = "Payment",
            body = "Receipt ready https://pay.example.com",
            timestamp = 1_764_000_000_000L
        )
        val xml = XmlArchiveBuilder.formatStructuredXmlQuery(
            userQuestion = "Total deposit?",
            source = AiChatContextSource.COMBINED_ARCHIVE,
            threadId = 5L,
            threadContactLabel = "Mellat",
            appPackageFilter = null,
            appNameFilter = null,
            rangeLabel = "One week",
            smsMessages = listOf(sms),
            notifications = listOf(notif),
            isPersian = false
        )
        assertTrue(xml.contains("<ai_query_request"))
        assertTrue(xml.contains("id=\"sms:12\""))
        assertTrue(xml.contains("Deposit 100 &amp; &lt;verified&gt;"))
        assertTrue(xml.contains("id=\"notif:8\""))
        assertEquals(BackupSyncMode.MANUAL, AppSettings().backupSyncMode)
        assertEquals(2, BackupSyncMode.entries.size)
    }

    @Test
    fun searchArchive_snapshotRoundTrip_preservesSmsAndNotificationResults() {
        val smsItem = SearchResultItem(
            refId = "sms:101",
            source = ArchiveItemSource.SMS,
            itemId = 101L,
            threadId = 7L,
            senderDisplayName = "Bank Mellat",
            senderSecondary = "+982100",
            body = "Code 5544 verified",
            timestamp = 1_764_000_000_000L,
            isOutgoing = false,
            deliveryStatus = DeliveryStatus.DELIVERED
        )
        val notifItem = SearchResultItem(
            refId = "notif:202",
            source = ArchiveItemSource.NOTIFICATION,
            itemId = 202L,
            threadId = null,
            senderDisplayName = "Snapp",
            senderSecondary = "Snapp!",
            body = "Trip receipt https://snapp.ir/r/1",
            timestamp = 1_764_000_120_000L,
            isOutgoing = false,
            deliveryStatus = DeliveryStatus.NONE
        )
        val encoded = ArchiveSearchRepository.encodeSnapshots(listOf(smsItem, notifItem))
        val decoded = ArchiveSearchRepository.decodeSnapshots(encoded)

        assertEquals(2, decoded.size)
        assertEquals(smsItem, decoded[0])
        assertEquals(notifItem, decoded[1])
        assertTrue(ArchiveSearchRepository.decodeSnapshots("").isEmpty())

        val history = SearchHistoryEntity(
            query = "Mellat",
            origin = "UNIFIED",
            resultCount = 2,
            matchedRefsCsv = "sms:101,notif:202",
            resultsSnapshotJson = encoded,
            timestamp = 1_764_000_200_000L
        )
        assertEquals("UNIFIED", history.origin)
        assertEquals(2, history.resultCount)

        val topic = AiTopicEntity(
            title = "Bank Mellat: OTP",
            createdAt = 1L,
            updatedAt = 2L,
            contextSource = AiChatContextSource.SELECTED_SMS_THREAD.name,
            selectedThreadId = 7L,
            originScreen = "AICHAT"
        )
        assertEquals("AICHAT", topic.originScreen)
        assertEquals(7L, topic.selectedThreadId)
    }
}
