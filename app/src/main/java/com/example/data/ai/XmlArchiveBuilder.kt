package com.example.data.ai

import com.example.core.localization.AppStrings
import com.example.core.localization.CalendarMode
import com.example.core.localization.DateUtils
import com.example.data.local.AiContextMode
import com.example.data.local.ArchiveRangePreset
import com.example.data.local.NotificationDao
import com.example.data.local.NotificationItem
import com.example.data.local.SmsDao
import com.example.data.local.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

data class XmlArchiveBuildResult(
    val xmlContent: String,
    val smsCount: Int,
    val notificationCount: Int,
    val totalBytes: Int,
    val startMs: Long,
    val endMs: Long,
    val rangeLabel: String,
    val contextMode: AiContextMode = AiContextMode.COMPACT_TEXT
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

    suspend fun buildContextForAi(
        question: String,
        contextMode: AiContextMode,
        preset: ArchiveRangePreset,
        customStartMs: Long,
        customEndMs: Long,
        maxItems: Int,
        includeSms: Boolean,
        includeNotifications: Boolean,
        isPersian: Boolean,
        strings: AppStrings
    ): XmlArchiveBuildResult {
        return when (contextMode) {
            AiContextMode.INLINE_XML -> buildXml(
                preset = preset,
                customStartMs = customStartMs,
                customEndMs = customEndMs,
                maxItems = maxItems,
                includeSms = includeSms,
                includeNotifications = includeNotifications,
                isPersian = isPersian,
                strings = strings
            )
            AiContextMode.COMPACT_TEXT,
            AiContextMode.KEYWORD_PREFILTER -> buildCompactPlainTextContext(
                question = question,
                keywordPriorityOnly = (contextMode == AiContextMode.KEYWORD_PREFILTER),
                contextMode = contextMode,
                preset = preset,
                customStartMs = customStartMs,
                customEndMs = customEndMs,
                maxItems = maxItems,
                includeSms = includeSms,
                includeNotifications = includeNotifications,
                isPersian = isPersian,
                strings = strings
            )
        }
    }

    private suspend fun buildCompactPlainTextContext(
        question: String,
        keywordPriorityOnly: Boolean,
        contextMode: AiContextMode,
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
        val fetchPoolSize = (safeMax * 3).coerceAtMost(1200)

        val keywords = extractSearchKeywords(question)
        val ftsQuery = buildFtsOrQuery(keywords)

        val smsPool = if (includeSms) {
            val baseList = smsDao.getSmsInRange(startMs, endMs, fetchPoolSize)
            val ftsHits = if (ftsQuery != null) {
                runCatching { smsDao.searchSmsFts(ftsQuery, startMs, endMs).first() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            (ftsHits + baseList).distinctBy { it.id }
        } else {
            emptyList()
        }

        val notifPool = if (includeNotifications) {
            val baseList = notificationDao.getNotificationsInRange(startMs, endMs, fetchPoolSize)
            val ftsHits = if (ftsQuery != null) {
                runCatching { notificationDao.searchNotificationsFts(ftsQuery, startMs, endMs).first() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            (ftsHits + baseList).distinctBy { it.id }
        } else {
            emptyList()
        }

        data class ScoredLine(val score: Int, val ts: Long, val line: String, val isSms: Boolean)

        val scored = ArrayList<ScoredLine>(smsPool.size + notifPool.size)
        for (sms in smsPool) {
            val score = scoreTextMatch(
                keywords = keywords,
                fields = listOf(sms.contactName.orEmpty(), sms.address, sms.sender, sms.body)
            )
            scored.add(ScoredLine(score, sms.timestamp, formatCompactSmsLine(sms), isSms = true))
        }
        for (notif in notifPool) {
            val score = scoreTextMatch(
                keywords = keywords,
                fields = listOf(notif.appName, notif.senderOrTitle, notif.body, notif.subText)
            )
            scored.add(ScoredLine(score, notif.timestamp, formatCompactNotifLine(notif), isSms = false))
        }

        val matchedCount = scored.count { it.score > 0 }
        val selected = if (keywordPriorityOnly && matchedCount > 0) {
            val targetCap = safeMax.coerceAtMost(120)
            val matches = scored.filter { it.score > 0 }
                .sortedWith(compareByDescending<ScoredLine> { it.score }.thenByDescending { it.ts })
                .take(targetCap)
            if (matches.size < targetCap / 2) {
                val extra = scored.filter { it.score == 0 }
                    .sortedByDescending { it.ts }
                    .take((targetCap - matches.size).coerceAtMost(35))
                matches + extra
            } else {
                matches
            }
        } else {
            scored.sortedWith(compareByDescending<ScoredLine> { it.score }.thenByDescending { it.ts })
                .take(safeMax)
        }

        val actualSmsCount = selected.count { it.isSms }
        val actualNotifCount = selected.count { !it.isSms }
        val rangeLabel = presetLabel(preset, startMs, endMs, isPersian, strings)
        val calLabel = if (isPersian) "jalali" else "gregorian"

        val textPayload = buildString {
            append("ARCHIVE DIGEST (Plain-Text, No File Upload)\n")
            append("Range: ").append(rangeLabel)
            append(" | Calendar: ").append(calLabel)
            append(" | Items: ").append(selected.size)
            append(" (SMS=").append(actualSmsCount).append(", Notifications=").append(actualNotifCount).append(")\n")
            append("Format: [id] date | dir_or_app | sender | content\n")
            append("---\n")
            for (entry in selected) {
                append(entry.line).append('\n')
            }
        }

        XmlArchiveBuildResult(
            xmlContent = textPayload,
            smsCount = actualSmsCount,
            notificationCount = actualNotifCount,
            totalBytes = textPayload.toByteArray(Charsets.UTF_8).size,
            startMs = startMs,
            endMs = endMs,
            rangeLabel = rangeLabel,
            contextMode = contextMode
        )
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
            rangeLabel = rangeLabel,
            contextMode = AiContextMode.INLINE_XML
        )
    }

    private fun formatCompactSmsLine(sms: SmsMessage): String {
        val sender = sms.contactName?.takeIf { it.isNotBlank() }?.let { "$it (${sms.address})" } ?: sms.address
        val dir = if (sms.isOutgoing) "OUT" else "IN"
        val dateJ = DateUtils.formatDateTime(sms.timestamp, CalendarMode.JALALI, usePersianDigits = false)
        val dateG = DateUtils.formatDateTime(sms.timestamp, CalendarMode.GREGORIAN, usePersianDigits = false)
        val cleanBody = sms.body.replace('\n', ' ').trim()
        return "[sms:${sms.id}] $dateJ ($dateG) | $dir | $sender | $cleanBody"
    }

    private fun formatCompactNotifLine(notif: NotificationItem): String {
        val dateJ = DateUtils.formatDateTime(notif.timestamp, CalendarMode.JALALI, usePersianDigits = false)
        val dateG = DateUtils.formatDateTime(notif.timestamp, CalendarMode.GREGORIAN, usePersianDigits = false)
        val cleanBody = buildString {
            append(notif.body.replace('\n', ' ').trim())
            if (notif.subText.isNotBlank()) append(" (${notif.subText.trim()})")
        }
        return "[notif:${notif.id}] $dateJ ($dateG) | ${notif.appName} | ${notif.senderOrTitle} | $cleanBody"
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
        private val stopWords = setOf(
            "the", "and", "for", "with", "from", "that", "this", "what", "which", "when",
            "where", "about", "find", "show", "search", "messages", "message", "sms", "notif",
            "notification", "notifications", "please", "tell", "have", "has", "was", "were",
            "از", "به", "در", "با", "برای", "که", "این", "آن", "های", "را", "می", "شد", "است",
            "هست", "بود", "کدام", "کدوم", "چند", "چقدر", "درباره", "پیامک", "پیام", "اعلان", "نوتیفیکیشن"
        )

        fun normalizeForMatch(input: String): String {
            if (input.isEmpty()) return ""
            val sb = StringBuilder(input.length)
            for (ch in input) {
                val mapped = when (ch) {
                    in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                    in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    'ة' -> 'ه'
                    'أ', 'إ', 'آ' -> 'ا'
                    else -> ch.lowercaseChar()
                }
                sb.append(mapped)
            }
            return sb.toString()
        }

        fun extractSearchKeywords(question: String): List<String> {
            val normalized = normalizeForMatch(question)
            return normalized
                .split(Regex("""[\s,.;:!?()\[\]{}"'«»/\\|+-]+"""))
                .map { it.trim() }
                .filter { it.length >= 2 && it !in stopWords }
                .distinct()
                .take(10)
        }

        fun buildFtsOrQuery(keywords: List<String>): String? {
            val ftsTerms = keywords
                .map { token -> token.filter { it.isLetterOrDigit() } }
                .filter { it.length >= 2 }
                .distinct()
            if (ftsTerms.isEmpty()) return null
            return ftsTerms.joinToString(" OR ") { "$it*" }
        }

        fun scoreTextMatch(keywords: List<String>, fields: List<String>): Int {
            if (keywords.isEmpty()) return 0
            val joined = normalizeForMatch(fields.joinToString(" "))
            var score = 0
            for (kw in keywords) {
                if (joined.contains(kw)) {
                    score += if (kw.any { it.isDigit() }) 4 else 2
                }
            }
            return score
        }

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
