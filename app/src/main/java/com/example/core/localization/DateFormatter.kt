package com.example.core.localization

import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated DateFormatter utility integrating the 'persian-date' library
 * to format SMS and AI chat timestamps in Jalali, Gregorian, or dual bilingual formats
 * across the UI and local archive exports.
 */
object DateFormatter {

    fun formatSmsTimestamp(
        timestampMs: Long,
        mode: CalendarMode = DateUtils.currentCalendarMode,
        usePersianDigits: Boolean = (mode == CalendarMode.JALALI)
    ): String = DateUtils.formatListTimestamp(
        timestampMs = timestampMs,
        mode = mode,
        isPersianLabels = usePersianDigits
    )

    fun formatChatTimestamp(
        timestampMs: Long,
        mode: CalendarMode = DateUtils.currentCalendarMode,
        usePersianDigits: Boolean = (mode == CalendarMode.JALALI)
    ): String = DateUtils.formatDateTime(
        timestampMs = timestampMs,
        mode = mode,
        usePersianDigits = usePersianDigits
    )

    fun formatJalali(
        timestampMs: Long,
        pattern: String = "Y/m/d H:i",
        usePersianDigits: Boolean = true
    ): String {
        val pDate = PersianDate(timestampMs)
        val formatted = PersianDateFormat(pattern).format(pDate)
        return if (usePersianDigits) DateUtils.toPersianDigits(formatted) else DateUtils.toEnglishDigits(formatted)
    }

    fun formatGregorian(
        timestampMs: Long,
        pattern: String = "yyyy/MM/dd HH:mm",
        usePersianDigits: Boolean = false
    ): String {
        val formatted = SimpleDateFormat(pattern, Locale.US).format(Date(timestampMs))
        return if (usePersianDigits) DateUtils.toPersianDigits(formatted) else formatted
    }

    /**
     * Formats a timestamp displaying BOTH Jalali and Gregorian dates simultaneously.
     */
    fun formatDualTimestamp(
        timestampMs: Long,
        primaryMode: CalendarMode = DateUtils.currentCalendarMode,
        usePersianDigits: Boolean = (primaryMode == CalendarMode.JALALI)
    ): String {
        val jalali = formatJalali(timestampMs, usePersianDigits = usePersianDigits)
        val gregorian = formatGregorian(timestampMs, usePersianDigits = false)
        return if (primaryMode == CalendarMode.JALALI) {
            "$jalali • $gregorian"
        } else {
            "$gregorian • $jalali"
        }
    }

    fun formatDualShortDate(
        timestampMs: Long,
        primaryMode: CalendarMode = DateUtils.currentCalendarMode,
        usePersianDigits: Boolean = (primaryMode == CalendarMode.JALALI)
    ): String {
        val jalali = formatJalali(timestampMs, pattern = "Y/m/d", usePersianDigits = usePersianDigits)
        val gregorian = formatGregorian(timestampMs, pattern = "yyyy-MM-dd", usePersianDigits = false)
        return if (primaryMode == CalendarMode.JALALI) {
            "$jalali ($gregorian)"
        } else {
            "$gregorian ($jalali)"
        }
    }
}
