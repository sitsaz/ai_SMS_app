package com.example.core.localization

import java.util.TimeZone

data class JalaliDate(
    val year: Int,
    val month: Int,
    val day: Int
)

object JalaliCalendar {

    fun toPersianDigits(input: String): String = DateUtils.toPersianDigits(input)

    fun toEnglishDigits(input: String): String = DateUtils.toEnglishDigits(input)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate =
        DateUtils.gregorianToJalali(gy, gm, gd)

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> =
        DateUtils.jalaliToGregorian(jy, jm, jd)

    fun isJalaliLeapYear(jy: Int): Boolean = DateUtils.isJalaliLeapYear(jy)

    fun daysInJalaliMonth(jy: Int, jm: Int): Int = DateUtils.daysInMonth(jy, jm, isPersian = true)

    fun timestampToJalali(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): JalaliDate {
        val (y, m, d) = DateUtils.getYearMonthDay(timestampMs, isPersian = true, timeZone = timeZone)
        return JalaliDate(y, m, d)
    }

    fun jalaliToTimestamp(
        jy: Int,
        jm: Int,
        jd: Int,
        endOfDay: Boolean = false,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long = DateUtils.toEpochMillis(jy, jm, jd, isPersian = true, endOfDay = endOfDay, timeZone = timeZone)

    fun persianMonthName(month: Int): String = DateUtils.monthName(month, isPersian = true)

    fun formatDateTime(timestampMs: Long, isPersian: Boolean): String =
        DateUtils.formatDateTime(timestampMs, isPersian)

    fun formatDateShort(timestampMs: Long, isPersian: Boolean): String =
        DateUtils.formatDateShort(timestampMs, isPersian)

    fun formatListTimestamp(timestampMs: Long, isPersian: Boolean): String =
        DateUtils.formatListTimestamp(timestampMs, isPersian)

    fun formatNumber(value: Number, isPersian: Boolean): String =
        DateUtils.formatNumber(value, isPersian)

    fun startOfTodayMillis(): Long = DateUtils.startOfTodayMillis()
}
