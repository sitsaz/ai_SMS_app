package com.example

import com.example.core.localization.AppLanguage
import com.example.core.localization.CalendarMode
import com.example.core.localization.DateUtils
import com.example.core.localization.JalaliDate
import com.example.core.localization.LocaleManager
import com.example.data.ai.XmlArchiveBuilder
import com.example.data.sms.ContactsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
