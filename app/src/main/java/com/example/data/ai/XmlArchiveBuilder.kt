package com.example.data.ai

import com.example.core.localization.AppStrings
import com.example.core.localization.CalendarMode
import com.example.core.localization.DateUtils
import com.example.data.local.AiChatContextSource
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
import kotlin.math.ceil

data class XmlArchiveBuildResult(
    val xmlContent: String,
    val smsCount: Int,
    val notificationCount: Int,
    val totalBytes: Int,
    val startMs: Long,
    val endMs: Long,
    val rangeLabel: String,
    val contextMode: AiContextMode = AiContextMode.SMART_AUTO,
    val estimatedTokens: Int = 0,
    val contextLimitTokens: Int = 32_000,
    val totalAvailableInRange: Int = smsCount + notificationCount,
    val fileUploadPayload: String? = null
) {
    val totalCount: Int get() = smsCount + notificationCount
}

data class StructuredXmlPromptResult(
    val xmlPrompt: String,
    val smsCount: Int,
    val notificationCount: Int,
    val totalBytes: Int,
    val sourceLabel: String,
    val estimatedTokens: Int = 0,
    val contextLimitTokens: Int = 32_000,
    val totalCandidatesInRange: Int = smsCount + notificationCount,
    val fileUploadXmlPayload: String? = null,
    val fileUploadTotalCount: Int = 0
) {
    val totalCount: Int get() = smsCount + notificationCount
}

object ContextBudgetCalculator {

    /**
     * Estimates LLM token count for mixed Persian/Arabic & English/XML content.
     */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        var asciiCount = 0
        var nonAsciiCount = 0
        for (ch in text) {
            if (ch.code <= 127) asciiCount++ else nonAsciiCount++
        }
        return ceil(asciiCount * 0.28 + nonAsciiCount * 0.85).toInt().coerceAtLeast(1)
    }

    fun availableMessageBudgetTokens(
        contextLimitTokens: Int,
        userQuestion: String,
        priorHistoryTokens: Int,
        overheadReserveTokens: Int = 1_200
    ): Int {
        val safeLimit = contextLimitTokens.coerceIn(1_024, 2_000_000)
        val completionReserve = (safeLimit * 0.15).toInt().coerceIn(512, 4_096)
        val usedByPrompt = overheadReserveTokens + estimateTokens(userQuestion) + priorHistoryTokens + completionReserve
        return (safeLimit - usedByPrompt).coerceAtLeast(450)
    }

    /**
     * Given records ordered NEWEST -> OLDEST, selects the newest N items whose
     * formatted text fits inside [availableTokens], returning them alongside token stats.
     */
    fun <T> selectNewestWithinBudget(
        newestFirstItems: List<T>,
        availableTokens: Int,
        formatItem: (T) -> String
    ): Pair<List<T>, Int> {
        if (newestFirstItems.isEmpty()) return emptyList<T>() to 0
        val selected = ArrayList<T>()
        var accumulatedTokens = 0
        for (item in newestFirstItems) {
            val formatted = formatItem(item)
            val tokens = estimateTokens(formatted) + 2
            if (selected.isNotEmpty() && accumulatedTokens + tokens > availableTokens) {
                break
            }
            selected.add(item)
            accumulatedTokens += tokens
            if (accumulatedTokens >= availableTokens) break
        }
        return selected to accumulatedTokens
    }
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
        strings: AppStrings,
        contextLimitTokens: Int = 32_000,
        priorHistoryTokens: Int = 0,
        preferFileUpload: Boolean = false
    ): XmlArchiveBuildResult {
        return when (contextMode) {
            AiContextMode.SMART_AUTO -> buildSmartContextBudgeted(
                question = question,
                preset = preset,
                customStartMs = customStartMs,
                customEndMs = customEndMs,
                includeSms = includeSms,
                includeNotifications = includeNotifications,
                isPersian = isPersian,
                strings = strings,
                contextLimitTokens = contextLimitTokens,
                priorHistoryTokens = priorHistoryTokens,
                preferFileUpload = preferFileUpload
            )
            AiContextMode.INLINE_XML -> buildXml(
                preset = preset,
                customStartMs = customStartMs,
                customEndMs = customEndMs,
                maxItems = maxItems,
                includeSms = includeSms,
                includeNotifications = includeNotifications,
                isPersian = isPersian,
                strings = strings,
                contextLimitTokens = contextLimitTokens
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
                strings = strings,
                contextLimitTokens = contextLimitTokens
            )
        }
    }

    private suspend fun buildSmartContextBudgeted(
        question: String,
        preset: ArchiveRangePreset,
        customStartMs: Long,
        customEndMs: Long,
        includeSms: Boolean,
        includeNotifications: Boolean,
        isPersian: Boolean,
        strings: AppStrings,
        contextLimitTokens: Int,
        priorHistoryTokens: Int,
        preferFileUpload: Boolean
    ): XmlArchiveBuildResult = withContext(Dispatchers.IO) {
        val (startMs, endMs) = resolveBounds(preset, customStartMs, customEndMs)
        val maxPool = 2000

        val smsPool = if (includeSms) smsDao.getSmsInRange(startMs, endMs, maxPool) else emptyList()
        val notifPool = if (includeNotifications) notificationDao.getNotificationsInRange(startMs, endMs, maxPool) else emptyList()

        data class SmartEntry(val ts: Long, val line: String, val xmlLine: String, val isSms: Boolean)

        val merged = ArrayList<SmartEntry>(smsPool.size + notifPool.size)
        for (sms in smsPool) {
            merged.add(
                SmartEntry(
                    ts = sms.timestamp,
                    line = formatCompactSmsLine(sms),
                    xmlLine = formatXmlSmsLine(sms),
                    isSms = true
                )
            )
        }
        for (notif in notifPool) {
            merged.add(
                SmartEntry(
                    ts = notif.timestamp,
                    line = formatCompactNotifLine(notif),
                    xmlLine = formatXmlNotifLine(notif),
                    isSms = false
                )
            )
        }
        // Sort newest first so we always keep the latest N messages that fit the API context budget
        merged.sortByDescending { it.ts }

        val availableTokens = ContextBudgetCalculator.availableMessageBudgetTokens(
            contextLimitTokens = contextLimitTokens,
            userQuestion = question,
            priorHistoryTokens = priorHistoryTokens
        )

        val (budgetedNewestFirst, usedTokens) = ContextBudgetCalculator.selectNewestWithinBudget(
            newestFirstItems = merged,
            availableTokens = availableTokens,
            formatItem = { it.line }
        )

        val chronological = budgetedNewestFirst.reversed()
        val actualSmsCount = chronological.count { it.isSms }
        val actualNotifCount = chronological.count { !it.isSms }
        val rangeLabel = presetLabel(preset, startMs, endMs, isPersian, strings)
        val calLabel = if (isPersian) "jalali" else "gregorian"

        val inlinePayload = buildString {
            append("SMART CONTEXT ARCHIVE (Last ").append(chronological.size)
            append(" of ").append(merged.size).append(" in range | Budget ~").append(availableTokens).append(" tok)\n")
            append("Range: ").append(rangeLabel)
            append(" | Calendar: ").append(calLabel)
            append(" | SMS=").append(actualSmsCount).append(", Notifications=").append(actualNotifCount).append('\n')
            append("---\n")
            for (entry in chronological) {
                append(entry.line).append('\n')
            }
        }

        val fullUploadXml = if (preferFileUpload && merged.isNotEmpty()) {
            // Also trim upload file softly only if ridiculously huge relative to huge context limits
            val fullChrono = merged.reversed()
            buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                append("<archive mode=\"full_file_upload\" calendar=\"").append(calLabel).append('"')
                append(" range=\"").append(escapeXml(rangeLabel)).append('"')
                append(" totalItems=\"").append(fullChrono.size).append("\">\n")
                for (entry in fullChrono) {
                    append(entry.xmlLine).append('\n')
                }
                append("</archive>")
            }
        } else {
            null
        }

        XmlArchiveBuildResult(
            xmlContent = inlinePayload,
            smsCount = actualSmsCount,
            notificationCount = actualNotifCount,
            totalBytes = (fullUploadXml ?: inlinePayload).toByteArray(Charsets.UTF_8).size,
            startMs = startMs,
            endMs = endMs,
            rangeLabel = rangeLabel,
            contextMode = AiContextMode.SMART_AUTO,
            estimatedTokens = usedTokens,
            contextLimitTokens = contextLimitTokens,
            totalAvailableInRange = merged.size,
            fileUploadPayload = fullUploadXml
        )
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
        strings: AppStrings,
        contextLimitTokens: Int = 32_000
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
            contextMode = contextMode,
            estimatedTokens = ContextBudgetCalculator.estimateTokens(textPayload),
            contextLimitTokens = contextLimitTokens,
            totalAvailableInRange = scored.size
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
        strings: AppStrings,
        contextLimitTokens: Int = 32_000
    ): XmlArchiveBuildResult = withContext(Dispatchers.IO) {
        val (startMs, endMs) = resolveBounds(preset, customStartMs, customEndMs)
        val safeMax = maxItems.coerceIn(10, 1000)

        val smsList = if (includeSms) smsDao.getSmsInRange(startMs, endMs, safeMax) else emptyList()
        val notifList = if (includeNotifications) notificationDao.getNotificationsInRange(startMs, endMs, safeMax) else emptyList()

        data class TaggedRecord(val ts: Long, val xmlLine: String, val isSms: Boolean)

        val merged = ArrayList<TaggedRecord>(smsList.size + notifList.size)
        for (sms in smsList) {
            merged.add(TaggedRecord(sms.timestamp, formatXmlSmsLine(sms), isSms = true))
        }
        for (notif in notifList) {
            merged.add(TaggedRecord(notif.timestamp, formatXmlNotifLine(notif), isSms = false))
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
            contextMode = AiContextMode.INLINE_XML,
            estimatedTokens = ContextBudgetCalculator.estimateTokens(xml),
            contextLimitTokens = contextLimitTokens,
            totalAvailableInRange = merged.size
        )
    }

    private fun formatXmlSmsLine(sms: SmsMessage): String {
        val sender = escapeXml(sms.contactName?.takeIf { it.isNotBlank() } ?: sms.address)
        val addr = escapeXml(sms.address)
        val dir = if (sms.isOutgoing) "outgoing" else "incoming"
        val dateG = DateUtils.formatDateTime(sms.timestamp, CalendarMode.GREGORIAN, usePersianDigits = false)
        val dateJ = DateUtils.formatDateTime(sms.timestamp, CalendarMode.JALALI, usePersianDigits = false)
        return buildString {
            append("  <sms id=\"sms:").append(sms.id).append('"')
            append(" threadId=\"").append(sms.threadId).append('"')
            append(" sender=\"").append(sender).append('"')
            append(" address=\"").append(addr).append('"')
            append(" direction=\"").append(dir).append('"')
            append(" status=\"").append(escapeXml(sms.deliveryStatus)).append('"')
            append(" dateGregorian=\"").append(escapeXml(dateG)).append('"')
            append(" dateJalali=\"").append(escapeXml(dateJ)).append('"')
            append('>')
            append(escapeXml(sms.body))
            append("</sms>")
        }
    }

    private fun formatXmlNotifLine(notif: NotificationItem): String {
        val sender = escapeXml(notif.senderOrTitle)
        val app = escapeXml(notif.appName)
        val pkg = escapeXml(notif.packageName)
        val dateG = DateUtils.formatDateTime(notif.timestamp, CalendarMode.GREGORIAN, usePersianDigits = false)
        val dateJ = DateUtils.formatDateTime(notif.timestamp, CalendarMode.JALALI, usePersianDigits = false)
        val fullText = if (notif.subText.isNotBlank()) "${notif.body} (${notif.subText})" else notif.body
        return buildString {
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

    suspend fun buildStructuredXmlPromptForSource(
        userQuestion: String,
        source: AiChatContextSource,
        selectedThreadId: Long?,
        selectedThreadLabel: String,
        selectedAppPackage: String?,
        selectedAppLabel: String?,
        preset: ArchiveRangePreset,
        customStartMs: Long,
        customEndMs: Long,
        maxItems: Int,
        isPersian: Boolean,
        strings: AppStrings,
        smartContextBudget: Boolean = true,
        contextLimitTokens: Int = 32_000,
        priorHistoryTokens: Int = 0,
        preferFileUpload: Boolean = false
    ): StructuredXmlPromptResult = withContext(Dispatchers.IO) {
        val poolCap = if (smartContextBudget) 1500 else maxItems.coerceIn(10, 500)
        val (startMs, endMs) = resolveBounds(preset, customStartMs, customEndMs)
        val rangeLabel = presetLabel(preset, startMs, endMs, isPersian, strings)

        // Newest-first pools so smart context budgeting keeps the latest N fitting messages
        val smsNewestFirst: List<SmsMessage>
        val notifNewestFirst: List<NotificationItem>
        val sourceLabel: String

        when (source) {
            AiChatContextSource.SELECTED_SMS_THREAD -> {
                smsNewestFirst = if (selectedThreadId != null && selectedThreadId > 0L) {
                    smsDao.getThreadMessagesForAi(selectedThreadId, poolCap)
                } else {
                    emptyList()
                }
                notifNewestFirst = emptyList()
                sourceLabel = selectedThreadLabel.ifBlank { strings.smsLabel }
            }
            AiChatContextSource.NOTIFICATION_HISTORY -> {
                smsNewestFirst = emptyList()
                notifNewestFirst = if (!selectedAppPackage.isNullOrBlank()) {
                    notificationDao.getNotificationsByPackageInRange(
                        packageName = selectedAppPackage,
                        startMs = startMs,
                        endMs = endMs,
                        limit = poolCap
                    )
                } else {
                    notificationDao.getNotificationsInRange(startMs, endMs, poolCap)
                }
                sourceLabel = selectedAppLabel?.takeIf { it.isNotBlank() } ?: strings.notificationHistoryLabel
            }
            AiChatContextSource.COMBINED_ARCHIVE -> {
                val halfMax = if (smartContextBudget) 1000 else (poolCap / 2).coerceAtLeast(10)
                smsNewestFirst = if (selectedThreadId != null && selectedThreadId > 0L) {
                    smsDao.getThreadMessagesForAi(selectedThreadId, halfMax)
                } else {
                    smsDao.getSmsInRange(startMs, endMs, halfMax)
                }
                notifNewestFirst = if (!selectedAppPackage.isNullOrBlank()) {
                    notificationDao.getNotificationsByPackageInRange(
                        packageName = selectedAppPackage,
                        startMs = startMs,
                        endMs = endMs,
                        limit = halfMax
                    )
                } else {
                    notificationDao.getNotificationsInRange(startMs, endMs, halfMax)
                }
                sourceLabel = rangeLabel
            }
        }

        val totalCandidates = smsNewestFirst.size + notifNewestFirst.size
        val selectedSms: List<SmsMessage>
        val selectedNotifs: List<NotificationItem>

        if (smartContextBudget) {
            val availableTokens = ContextBudgetCalculator.availableMessageBudgetTokens(
                contextLimitTokens = contextLimitTokens,
                userQuestion = userQuestion,
                priorHistoryTokens = priorHistoryTokens
            )
            val merged = buildList {
                smsNewestFirst.forEach { sms ->
                    add(Triple(sms.timestamp, "sms:${sms.id}", formatXmlSmsLine(sms)))
                }
                notifNewestFirst.forEach { n ->
                    add(Triple(n.timestamp, "notif:${n.id}", formatXmlNotifLine(n)))
                }
            }.sortedByDescending { it.first }

            val (fitting, _) = ContextBudgetCalculator.selectNewestWithinBudget(
                newestFirstItems = merged,
                availableTokens = availableTokens,
                formatItem = { it.third }
            )
            val fittingTags = fitting.mapTo(HashSet()) { it.second }
            selectedSms = smsNewestFirst.filter { sms ->
                "sms:${sms.id}" in fittingTags
            }.sortedBy { it.timestamp }
            selectedNotifs = notifNewestFirst.filter { n ->
                "notif:${n.id}" in fittingTags
            }.sortedByDescending { it.timestamp }
        } else {
            selectedSms = smsNewestFirst.reversed()
            selectedNotifs = notifNewestFirst
        }

        val xmlPrompt = formatStructuredXmlQuery(
            userQuestion = userQuestion,
            source = source,
            threadId = selectedThreadId,
            threadContactLabel = selectedThreadLabel,
            appPackageFilter = selectedAppPackage,
            appNameFilter = selectedAppLabel,
            rangeLabel = rangeLabel,
            smsMessages = selectedSms,
            notifications = selectedNotifs,
            isPersian = isPersian
        )

        val uploadXmlPayload = if (preferFileUpload && totalCandidates > 0) {
            formatStructuredXmlQuery(
                userQuestion = userQuestion,
                source = source,
                threadId = selectedThreadId,
                threadContactLabel = selectedThreadLabel,
                appPackageFilter = selectedAppPackage,
                appNameFilter = selectedAppLabel,
                rangeLabel = rangeLabel,
                smsMessages = smsNewestFirst.reversed(),
                notifications = notifNewestFirst,
                isPersian = isPersian
            )
        } else {
            null
        }

        StructuredXmlPromptResult(
            xmlPrompt = xmlPrompt,
            smsCount = selectedSms.size,
            notificationCount = selectedNotifs.size,
            totalBytes = (uploadXmlPayload ?: xmlPrompt).toByteArray(Charsets.UTF_8).size,
            sourceLabel = sourceLabel,
            estimatedTokens = ContextBudgetCalculator.estimateTokens(xmlPrompt),
            contextLimitTokens = contextLimitTokens,
            totalCandidatesInRange = totalCandidates,
            fileUploadXmlPayload = uploadXmlPayload,
            fileUploadTotalCount = totalCandidates
        )
    }

    companion object {
        private val stopWords = setOf(
            "the", "and", "for", "with", "from", "that", "this", "what", "which", "when",
            "where", "about", "find", "show", "search", "messages", "message", "sms", "notif",
            "notification", "notifications", "please", "tell", "have", "has", "was", "were",
            "از", "به", "در", "با", "برای", "که", "این", "آن", "های", "را", "می", "شد", "است",
            "هست", "بود", "کدام", "کدوم", "چند", "چقدر", "درباره", "پیامک", "پیام", "اعلان", "نوتیفیکیشن"
        )

        fun formatStructuredXmlQuery(
            userQuestion: String,
            source: AiChatContextSource,
            threadId: Long?,
            threadContactLabel: String,
            appPackageFilter: String?,
            appNameFilter: String?,
            rangeLabel: String,
            smsMessages: List<SmsMessage>,
            notifications: List<NotificationItem>,
            isPersian: Boolean
        ): String {
            val calAttr = if (isPersian) "jalali" else "gregorian"
            val sourceAttr = when (source) {
                AiChatContextSource.SELECTED_SMS_THREAD -> "sms_thread"
                AiChatContextSource.NOTIFICATION_HISTORY -> "notification_history"
                AiChatContextSource.COMBINED_ARCHIVE -> "combined_archive"
            }
            val nowFormatted = DateUtils.formatDateTime(
                System.currentTimeMillis(),
                if (isPersian) CalendarMode.JALALI else CalendarMode.GREGORIAN,
                usePersianDigits = false
            )

            return buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                append("<ai_query_request calendar=\"").append(calAttr).append('"')
                append(" source=\"").append(sourceAttr).append('"')
                append(" generatedAt=\"").append(escapeXml(nowFormatted)).append("\">\n")
                append("  <user_query>").append(escapeXml(userQuestion.trim())).append("</user_query>\n")
                append("  <context_scope>\n")
                append("    <source_type>").append(sourceAttr).append("</source_type>\n")
                if (threadId != null && threadId > 0L) {
                    append("    <thread_id>").append(threadId).append("</thread_id>\n")
                    if (threadContactLabel.isNotBlank()) {
                        append("    <contact>").append(escapeXml(threadContactLabel)).append("</contact>\n")
                    }
                }
                if (!appPackageFilter.isNullOrBlank()) {
                    append("    <app_filter package=\"").append(escapeXml(appPackageFilter)).append("\">")
                    append(escapeXml(appNameFilter ?: appPackageFilter))
                    append("</app_filter>\n")
                }
                if (rangeLabel.isNotBlank()) {
                    append("    <time_range>").append(escapeXml(rangeLabel)).append("</time_range>\n")
                }
                append("    <sms_count>").append(smsMessages.size).append("</sms_count>\n")
                append("    <notification_count>").append(notifications.size).append("</notification_count>\n")
                append("  </context_scope>\n")

                if (smsMessages.isNotEmpty()) {
                    append("  <conversation_history>\n")
                    for (sms in smsMessages) {
                        val sender = escapeXml(sms.contactName?.takeIf { it.isNotBlank() } ?: sms.address)
                        val dir = if (sms.isOutgoing) "outgoing" else "incoming"
                        val dateG = DateUtils.formatDateTime(sms.timestamp, CalendarMode.GREGORIAN, usePersianDigits = false)
                        val dateJ = DateUtils.formatDateTime(sms.timestamp, CalendarMode.JALALI, usePersianDigits = false)
                        append("    <sms id=\"sms:").append(sms.id).append('"')
                        append(" threadId=\"").append(sms.threadId).append('"')
                        append(" sender=\"").append(sender).append('"')
                        append(" address=\"").append(escapeXml(sms.address)).append('"')
                        append(" direction=\"").append(dir).append('"')
                        append(" status=\"").append(escapeXml(sms.deliveryStatus)).append('"')
                        append(" dateGregorian=\"").append(escapeXml(dateG)).append('"')
                        append(" dateJalali=\"").append(escapeXml(dateJ)).append("\">")
                        append(escapeXml(sms.body))
                        append("</sms>\n")
                    }
                    append("  </conversation_history>\n")
                }

                if (notifications.isNotEmpty()) {
                    append("  <notification_history>\n")
                    for (notif in notifications) {
                        val dateG = DateUtils.formatDateTime(notif.timestamp, CalendarMode.GREGORIAN, usePersianDigits = false)
                        val dateJ = DateUtils.formatDateTime(notif.timestamp, CalendarMode.JALALI, usePersianDigits = false)
                        val fullText = if (notif.subText.isNotBlank()) "${notif.body} (${notif.subText})" else notif.body
                        append("    <notification id=\"notif:").append(notif.id).append('"')
                        append(" app=\"").append(escapeXml(notif.appName)).append('"')
                        append(" package=\"").append(escapeXml(notif.packageName)).append('"')
                        append(" sender=\"").append(escapeXml(notif.senderOrTitle)).append('"')
                        append(" dateGregorian=\"").append(escapeXml(dateG)).append('"')
                        append(" dateJalali=\"").append(escapeXml(dateJ)).append("\">")
                        append(escapeXml(fullText))
                        append("</notification>\n")
                    }
                    append("  </notification_history>\n")
                }

                append("</ai_query_request>")
            }
        }

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
