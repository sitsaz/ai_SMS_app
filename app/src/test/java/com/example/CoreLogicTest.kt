package com.example

import com.example.core.localization.AppLanguage
import com.example.core.localization.CalendarMode
import com.example.core.localization.DateUtils
import com.example.core.localization.JalaliDate
import com.example.core.localization.LocaleManager
import com.example.data.ai.AiSearchClient
import com.example.data.ai.AiXmlResponseParser
import com.example.data.ai.DynamicAiRetrofitProvider
import com.example.data.ai.XmlArchiveBuilder
import com.example.data.local.AiProviderType
import com.example.data.preferences.AppSettings
import com.example.data.sms.ContactsRepository
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
}
