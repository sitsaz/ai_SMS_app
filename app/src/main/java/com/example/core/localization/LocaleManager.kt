package com.example.core.localization

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.LayoutDirection
import java.util.TimeZone

/**
 * Coordinates app language, layout direction, localized strings, and dynamic
 * Gregorian (English) vs. Jalali (Persian) date formatting powered by DateUtils & PersianDate.
 */
class LocaleManager(
    val language: AppLanguage = AppLanguage.SYSTEM
) {
    val isPersian: Boolean = language.resolveIsPersian()

    val calendarMode: CalendarMode = DateUtils.calendarModeFor(isPersian)

    val layoutDirection: LayoutDirection =
        if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr

    val strings: AppStrings =
        if (isPersian) persianStrings() else englishStrings()

    val calendarName: String =
        if (isPersian) "jalali" else "gregorian"

    fun formatDateTime(timestampMs: Long): String =
        DateUtils.formatDateTime(timestampMs, isPersian)

    fun formatDateShort(timestampMs: Long): String =
        DateUtils.formatDateShort(timestampMs, isPersian)

    fun formatListTimestamp(timestampMs: Long): String =
        DateUtils.formatListTimestamp(timestampMs, isPersian)

    fun formatNumber(value: Number): String =
        DateUtils.formatNumber(value, isPersian)

    fun getYearMonthDay(
        timestampMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Triple<Int, Int, Int> = DateUtils.getYearMonthDay(timestampMs, isPersian, timeZone)

    fun toEpochMillis(
        year: Int,
        month: Int,
        day: Int,
        endOfDay: Boolean = false,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long = DateUtils.toEpochMillis(year, month, day, isPersian, endOfDay, timeZone)
}

val LocalLocaleManager = staticCompositionLocalOf { LocaleManager(AppLanguage.ENGLISH) }
