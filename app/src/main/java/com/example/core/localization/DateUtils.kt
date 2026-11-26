package com.example.core.localization

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class CalendarMode {
    GREGORIAN,
    JALALI
}

enum class CalendarPreference {
    AUTO,
    JALALI,
    GREGORIAN;

    fun resolve(isPersianLanguage: Boolean): CalendarMode = when (this) {
        AUTO -> if (isPersianLanguage) CalendarMode.JALALI else CalendarMode.GREGORIAN
        JALALI -> CalendarMode.JALALI
        GREGORIAN -> CalendarMode.GREGORIAN
    }
}

val LocalCalendarMode = staticCompositionLocalOf { CalendarMode.GREGORIAN }

/**
 * Unified calendar & date formatting utility powered by com.github.samanzamani:PersianDate.
 * Reactively follows the global CalendarMode StateFlow / DataStore preference to toggle
 * between Jalali and Gregorian calendars across the UI.
 */
object DateUtils {

    private val _calendarModeFlow = MutableStateFlow(CalendarMode.GREGORIAN)
    val calendarModeFlow: StateFlow<CalendarMode> = _calendarModeFlow.asStateFlow()

    val currentCalendarMode: CalendarMode
        get() = _calendarModeFlow.value

    fun updateCalendarMode(mode: CalendarMode) {
        _calendarModeFlow.value = mode
    }

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val englishShortMonths = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun calendarModeFor(isPersian: Boolean): CalendarMode =
        if (isPersian) CalendarMode.JALALI else CalendarMode.GREGORIAN

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(persianDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun toEnglishDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatNumber(value: Number, isPersian: Boolean): String {
        val raw = value.toString()
        return if (isPersian) toPersianDigits(raw) else raw
    }

    fun formatDateTime(timestampMs: Long, isPersian: Boolean): String {
        return formatDateTime(timestampMs, calendarModeFor(isPersian), usePersianDigits = isPersian)
    }

    fun formatDateTime(
        timestampMs: Long,
        mode: CalendarMode = currentCalendarMode,
        usePersianDigits: Boolean = (mode == CalendarMode.JALALI)
    ): String {
        return when (mode) {
            CalendarMode.JALALI -> {
                val raw = formatJalaliDateTime(timestampMs)
                if (usePersianDigits) raw else toEnglishDigits(raw)
            }
            CalendarMode.GREGORIAN -> {
                val raw = formatGregorianDateTime(timestampMs)
                if (usePersianDigits) toPersianDigits(raw) else raw
            }
        }
    }

    fun formatJalaliDateTime(timestampMs: Long): String {
        val pDate = PersianDate(timestampMs)
        return toPersianDigits(PersianDateFormat("Y/m/d H:i").format(pDate))
    }

    fun formatGregorianDateTime(timestampMs: Long): String {
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(timestampMs))
    }

    fun formatDualDateTime(timestampMs: Long, isPersianPrimary: Boolean): String {
        val jalali = formatJalaliDateTime(timestampMs)
        val gregorian = formatGregorianDateTime(timestampMs)
        return if (isPersianPrimary) "$jalali ($gregorian)" else "$gregorian ($jalali)"
    }

    fun formatDateShort(timestampMs: Long, isPersian: Boolean): String {
        return formatDateShort(timestampMs, calendarModeFor(isPersian), usePersianDigits = isPersian)
    }

    fun formatDateShort(
        timestampMs: Long,
        mode: CalendarMode = currentCalendarMode,
        usePersianDigits: Boolean = (mode == CalendarMode.JALALI)
    ): String {
        return when (mode) {
            CalendarMode.JALALI -> {
                val pDate = PersianDate(timestampMs)
                val formatted = PersianDateFormat("j F Y").format(pDate)
                if (usePersianDigits) toPersianDigits(formatted) else toEnglishDigits(formatted)
            }
            CalendarMode.GREGORIAN -> {
                val formatted = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timestampMs))
                if (usePersianDigits) toPersianDigits(formatted) else formatted
            }
        }
    }

    fun formatListTimestamp(timestampMs: Long, isPersian: Boolean): String {
        return formatListTimestamp(timestampMs, calendarModeFor(isPersian), isPersianLabels = isPersian)
    }

    fun formatListTimestamp(
        timestampMs: Long,
        mode: CalendarMode = currentCalendarMode,
        isPersianLabels: Boolean = (mode == CalendarMode.JALALI)
    ): String {
        val now = System.currentTimeMillis()
        val todayStart = startOfTodayMillis()
        val yesterdayStart = todayStart - 86_400_000L
        return when {
            timestampMs >= todayStart -> {
                val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
                val raw = String.format(
                    Locale.US,
                    "%02d:%02d",
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE)
                )
                if (isPersianLabels) toPersianDigits(raw) else raw
            }
            timestampMs >= yesterdayStart -> if (isPersianLabels) "دیروز" else "Yesterday"
            else -> {
                when (mode) {
                    CalendarMode.JALALI -> {
                        val pMsg = PersianDate(timestampMs)
                        val pNow = PersianDate(now)
                        val formatted = if (pMsg.shYear == pNow.shYear) {
                            PersianDateFormat("j F").format(pMsg)
                        } else {
                            PersianDateFormat("Y/m/d").format(pMsg)
                        }
                        if (isPersianLabels) toPersianDigits(formatted) else toEnglishDigits(formatted)
                    }
                    CalendarMode.GREGORIAN -> {
                        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
                        val msgCal = Calendar.getInstance().apply { timeInMillis = timestampMs }
                        val pattern = if (nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)) "MMM d" else "MMM d, yy"
                        val formatted = SimpleDateFormat(pattern, Locale.US).format(Date(timestampMs))
                        if (isPersianLabels) toPersianDigits(formatted) else formatted
                    }
                }
            }
        }
    }

    fun toPersianDate(date: Date): PersianDate = PersianDate(date)

    fun toPersianDate(timestampMs: Long): PersianDate = PersianDate(timestampMs)

    fun toGregorianDate(
        jy: Int,
        jm: Int,
        jd: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Date {
        val (gy, gm, gd) = jalaliToGregorian(jy, jm, jd)
        return Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    fun toGregorianDate(jalali: JalaliDate, timeZone: TimeZone = TimeZone.getDefault()): Date =
        toGregorianDate(jalali.year, jalali.month, jalali.day, timeZone)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val pDate = PersianDate().apply { initGrgDate(gy, gm, gd) }
        return JalaliDate(pDate.shYear, pDate.shMonth, pDate.shDay)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val pDate = PersianDate().apply { initJalaliDate(jy, jm, jd) }
        return Triple(pDate.grgYear, pDate.grgMonth, pDate.grgDay)
    }

    fun isJalaliLeapYear(jy: Int): Boolean {
        return PersianDate().apply { initJalaliDate(jy, 1, 1) }.isLeap
    }

    fun daysInMonth(year: Int, month: Int, isPersian: Boolean): Int {
        return daysInMonth(year, month, calendarModeFor(isPersian))
    }

    fun daysInMonth(year: Int, month: Int, mode: CalendarMode = currentCalendarMode): Int {
        val safeMonth = month.coerceIn(1, 12)
        return when (mode) {
            CalendarMode.JALALI -> {
                PersianDate().apply { initJalaliDate(year, safeMonth, 1) }.monthDays
            }
            CalendarMode.GREGORIAN -> {
                when (safeMonth) {
                    2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
                    4, 6, 9, 11 -> 30
                    else -> 31
                }
            }
        }
    }

    fun monthName(month: Int, isPersian: Boolean): String {
        return monthName(month, calendarModeFor(isPersian))
    }

    fun monthName(month: Int, mode: CalendarMode = currentCalendarMode): String {
        val safeMonth = month.coerceIn(1, 12)
        return when (mode) {
            CalendarMode.JALALI -> {
                PersianDate().apply { initJalaliDate(1405, safeMonth, 1) }.monthName()
            }
            CalendarMode.GREGORIAN -> {
                englishShortMonths.getOrElse(safeMonth - 1) { safeMonth.toString() }
            }
        }
    }

    fun getYearMonthDay(
        timestampMs: Long,
        isPersian: Boolean,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Triple<Int, Int, Int> {
        return getYearMonthDay(timestampMs, calendarModeFor(isPersian), timeZone)
    }

    fun getYearMonthDay(
        timestampMs: Long,
        mode: CalendarMode = currentCalendarMode,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = timestampMs }
        return when (mode) {
            CalendarMode.JALALI -> {
                val j = gregorianToJalali(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                Triple(j.year, j.month, j.day)
            }
            CalendarMode.GREGORIAN -> {
                Triple(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)
                )
            }
        }
    }

    fun toEpochMillis(
        year: Int,
        month: Int,
        day: Int,
        isPersian: Boolean,
        endOfDay: Boolean = false,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        return toEpochMillis(year, month, day, calendarModeFor(isPersian), endOfDay, timeZone)
    }

    fun toEpochMillis(
        year: Int,
        month: Int,
        day: Int,
        mode: CalendarMode = currentCalendarMode,
        endOfDay: Boolean = false,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val safeMonth = month.coerceIn(1, 12)
        val safeDay = day.coerceIn(1, daysInMonth(year, safeMonth, mode))
        val (gy, gm, gd) = when (mode) {
            CalendarMode.JALALI -> jalaliToGregorian(year, safeMonth, safeDay)
            CalendarMode.GREGORIAN -> Triple(year, safeMonth, safeDay)
        }
        return Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            if (endOfDay) {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            } else {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }.timeInMillis
    }

    fun startOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
