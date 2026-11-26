package com.example.data.ai

import com.example.core.localization.AppStrings
import com.example.core.localization.DateUtils
import com.example.data.local.ArchiveRangePreset
import com.example.data.local.NotificationDao
import com.example.data.local.SmsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class XmlArchiveBuildResult(
    val xmlContent: String,
    val smsCount: Int,
    val notificationCount: Int,
    val totalBytes: Int,
    val startMs: Long,
    val endMs: Long,
    val rangeLabel: String
) {
    val totalCount: Int get() = smsCount + notificationCount
}

class XmlArchiveBuilder(
    private val smsDao: SmsDao,
    private val notificationDao: NotificationDao
) {

    fun resolveBounds(
        preset: ArchiveRangePreset,
        customStartMs: Long,
        customEndMs: Long
    ): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val todayStart = DateUtils.startOfTodayMillis()
        val dayMs = 86_400_000L

        return when (preset) {
            ArchiveRangePreset.TODAY -> todayStart to now
            ArchiveRangePreset.TWO_DAYS -> (todayStart - dayMs) to now
            ArchiveRangePreset.THREE_DAYS -> (todayStart - 2L * dayMs) to now
            ArchiveRangePreset.ONE_WEEK -> (todayStart - 6L * dayMs) to now
            ArchiveRangePreset.TWO_WEEKS -> (todayStart - 13L * dayMs) to now
            ArchiveRangePreset.ONE_MONTH -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    add(Calendar.MONTH, -1)
                }
                cal.timeInMillis to now
            }
            ArchiveRangePreset.ONE_YEAR -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    add(Calendar.YEAR, -1)
                }
                cal.timeInMillis to now
            }
            ArchiveRangePreset.CUSTOM -> {
                val s = if (customStartMs > 0L) customStartMs else (todayStart - 6L * dayMs)
                val e = if (customEndMs >= s) customEndMs else now
                s to e
            }
        }
    }

    suspend fun countInWindow(
        preset: ArchiveRangePreset,
        customStartMs: Long,
        customEndMs: Long,
        includeSms: Boolean,
        includeNotifications: Boolean
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val (startMs, endMs) = resolveBounds(preset, customStartMs, customEndMs)
        val smsCount = if (includeSms) smsDao.countSmsInRange(startMs, endMs) else 0
        val notifCount = if (includeNotifications) notificationDao.countNotificationsInRange(startMs, endMs) else 0
        smsCount to notifCount
    }

    suspend fun buildXml(
        preset: ArchiveRangePreset,
        customStartMs: Long,
        customEndMs: Long,
        maxItems: Int,
        includeSms: Boolean,
        includeNotifications: Boolean,
        isPersian: Boolean,
        strings: AppStrings
    ): XmlArchiveBuildResult = withContext(Dispatchers.IO) {
        val (startMs, endMs) = resolveBounds(preset, customStartMs, customEndMs)
        val safeMax = maxItems.coerceIn(10, 1000)

        val smsList = if (includeSms) smsDao.getSmsInRange(startMs, endMs, safeMax) else emptyList()
        val notifList = if (includeNotifications) notificationDao.getNotificationsInRange(startMs, endMs, safeMax) else emptyList()

        data class TaggedRecord(val ts: Long, val xmlLine: String, val isSms: Boolean)

        val merged = ArrayList<TaggedRecord>(smsList.size + notifList.size)
        for (sms in smsList) {
            val sender = escapeXml(sms.contactName?.takeIf { it.isNotBlank() } ?: sms.address)
            val addr = escapeXml(sms.address)
            val dir = if (sms.isOutgoing) "outgoing" else "incoming"
            val dateG = DateUtils.formatDateTime(sms.timestamp, isPersian = false)
            val dateJ = DateUtils.formatDateTime(sms.timestamp, isPersian = true)
            val body = escapeXml(sms.body)
            val line = buildString {
                append("  <sms id=\"sms:").append(sms.id).append('"')
                append(" threadId=\"").append(sms.threadId).append('"')
                append(" sender=\"").append(sender).append('"')
                append(" address=\"").append(addr).append('"')
                append(" direction=\"").append(dir).append('"')
                append(" status=\"").append(escapeXml(sms.deliveryStatus)).append('"')
                append(" dateGregorian=\"").append(escapeXml(dateG)).append('"')
                append(" dateJalali=\"").append(escapeXml(dateJ)).append('"')
                append('>')
                append(body)
                append("</sms>")
            }
            merged.add(TaggedRecord(sms.timestamp, line, isSms = true))
        }

        for (notif in notifList) {
            val sender = escapeXml(notif.senderOrTitle)
            val app = escapeXml(notif.appName)
            val pkg = escapeXml(notif.packageName)
            val dateG = DateUtils.formatDateTime(notif.timestamp, isPersian = false)
            val dateJ = DateUtils.formatDateTime(notif.timestamp, isPersian = true)
            val fullText = if (notif.subText.isNotBlank()) "${notif.body} (${notif.subText})" else notif.body
            val line = buildString {
                append("  <notification id=\"notif:").append(notif.id).append('"')
                append(" app=\"").append(app).append('"')
                append(" package=\"").append(pkg).append('"')
                append(" sender=\"").append(sender).append('"')
                append(" dateGregorian=\"").append(escapeXml(dateG)).append('"')
                append(" dateJalali=\"").append(escapeXml(dateJ)).append('"')
                append('>')
                append(escapeXml(fullText))
                append("</notification>")
            }
            merged.add(TaggedRecord(notif.timestamp, line, isSms = false))
        }

        merged.sortByDescending { it.ts }
        val capped = merged.take(safeMax)

        val actualSmsCount = capped.count { it.isSms }
        val actualNotifCount = capped.count { !it.isSms }
        val rangeLabel = presetLabel(preset, startMs, endMs, isPersian, strings)

        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<archive calendar=\"").append(if (isPersian) "jalali" else "gregorian").append('"')
            append(" range=\"").append(escapeXml(rangeLabel)).append('"')
            append(" totalItems=\"").append(capped.size).append('"')
            append(" smsCount=\"").append(actualSmsCount).append('"')
            append(" notificationCount=\"").append(actualNotifCount).append('"')
            append(" generatedAt=\"").append(escapeXml(DateUtils.formatDateTime(System.currentTimeMillis(), isPersian))).append("\">\n")
            for (item in capped) {
                append(item.xmlLine).append('\n')
            }
            append("</archive>")
        }

        XmlArchiveBuildResult(
            xmlContent = xml,
            smsCount = actualSmsCount,
            notificationCount = actualNotifCount,
            totalBytes = xml.toByteArray(Charsets.UTF_8).size,
            startMs = startMs,
            endMs = endMs,
            rangeLabel = rangeLabel
        )
    }

    fun presetLabel(
        preset: ArchiveRangePreset,
        startMs: Long,
        endMs: Long,
        isPersian: Boolean,
        strings: AppStrings
    ): String = when (preset) {
        ArchiveRangePreset.TODAY -> strings.presetToday
        ArchiveRangePreset.TWO_DAYS -> strings.presetTwoDays
        ArchiveRangePreset.THREE_DAYS -> strings.presetThreeDays
        ArchiveRangePreset.ONE_WEEK -> strings.presetOneWeek
        ArchiveRangePreset.TWO_WEEKS -> strings.presetTwoWeeks
        ArchiveRangePreset.ONE_MONTH -> strings.presetOneMonth
        ArchiveRangePreset.ONE_YEAR -> strings.presetOneYear
        ArchiveRangePreset.CUSTOM -> {
            val s = DateUtils.formatDateShort(startMs, isPersian)
            val e = DateUtils.formatDateShort(endMs, isPersian)
            "$s – $e"
        }
    }

    companion object {
        fun escapeXml(input: String): String {
            if (input.isEmpty()) return ""
            val sb = StringBuilder(input.length + 16)
            for (ch in input) {
                when (ch) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    '"' -> sb.append("&quot;")
                    '\'' -> sb.append("&apos;")
                    else -> {
                        if (ch.code == 0x9 || ch.code == 0xA || ch.code == 0xD || ch.code >= 0x20) {
                            sb.append(ch)
                        }
                    }
                }
            }
            return sb.toString()
        }
    }
}
