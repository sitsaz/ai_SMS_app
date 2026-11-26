package com.example.data.archive

import com.example.core.localization.CalendarMode
import com.example.core.localization.DateFormatter
import com.example.data.local.AiTopicWithMessages
import com.example.data.local.SearchHistoryEntity
import com.example.data.search.SearchResultItem
import org.json.JSONArray
import org.json.JSONObject

enum class ConversationExportFormat(val extension: String, val mimeType: String) {
    TEXT("txt", "text/plain"),
    JSON("json", "application/json")
}

/**
 * Formats specific AI chat threads, SMS thread archives (with linked AI history),
 * and saved search snapshots into human-readable Plain Text or pretty-printed JSON files.
 */
object ConversationExportFormatter {

    fun formatAiChatTopic(
        topicWithMessages: AiTopicWithMessages,
        format: ConversationExportFormat,
        isPersian: Boolean
    ): String {
        val topic = topicWithMessages.topic
        val messages = topicWithMessages.messages.sortedWith(compareBy({ it.timestamp }, { it.id }))
        val primaryMode = if (isPersian) CalendarMode.JALALI else CalendarMode.GREGORIAN

        return when (format) {
            ConversationExportFormat.JSON -> {
                val root = JSONObject().apply {
                    put("exportType", "ai_chat_thread")
                    put("exportedAtMs", System.currentTimeMillis())
                    put("exportedAtJalali", DateFormatter.formatJalali(System.currentTimeMillis(), usePersianDigits = false))
                    put("exportedAtGregorian", DateFormatter.formatGregorian(System.currentTimeMillis()))
                    put("topic", JSONObject().apply {
                        put("id", topic.id)
                        put("title", topic.title)
                        put("contextSource", topic.contextSource)
                        put("contextMode", topic.contextMode)
                        put("rangePreset", topic.rangePreset)
                        put("selectedThreadId", topic.selectedThreadId ?: JSONObject.NULL)
                        put("createdAtJalali", DateFormatter.formatJalali(topic.createdAt, usePersianDigits = false))
                        put("createdAtGregorian", DateFormatter.formatGregorian(topic.createdAt))
                        put("updatedAtJalali", DateFormatter.formatJalali(topic.updatedAt, usePersianDigits = false))
                        put("updatedAtGregorian", DateFormatter.formatGregorian(topic.updatedAt))
                    })
                    put("messages", JSONArray().apply {
                        for (msg in messages) {
                            put(JSONObject().apply {
                                put("id", msg.id)
                                put("role", msg.role)
                                put("text", msg.text)
                                put("timestampMs", msg.timestamp)
                                put("timestampJalali", DateFormatter.formatJalali(msg.timestamp, usePersianDigits = false))
                                put("timestampGregorian", DateFormatter.formatGregorian(msg.timestamp))
                                put("xmlSummaryLabel", msg.xmlSummaryLabel)
                                put("xmlContent", msg.xmlContent)
                                put("matchedRefsCsv", msg.matchedRefsCsv)
                                put("isError", msg.isError)
                            })
                        }
                    })
                }
                root.toString(2)
            }

            ConversationExportFormat.TEXT -> buildString {
                appendLine("============================================================")
                appendLine(if (isPersian) "آرشیو گفتگوی هوش مصنوعی: ${topic.title}" else "AI CHAT ARCHIVE: ${topic.title}")
                appendLine("Source: ${topic.contextSource} | Mode: ${topic.contextMode} | Range: ${topic.rangePreset}")
                appendLine("Updated: ${DateFormatter.formatDualTimestamp(topic.updatedAt, primaryMode, usePersianDigits = isPersian)}")
                appendLine("============================================================")
                appendLine()
                for (msg in messages) {
                    val speaker = if (msg.role == "USER") {
                        if (isPersian) "شما" else "USER"
                    } else {
                        if (isPersian) "هوش مصنوعی" else "ASSISTANT"
                    }
                    val ts = DateFormatter.formatDualTimestamp(msg.timestamp, primaryMode, usePersianDigits = isPersian)
                    appendLine("[$speaker] ($ts)")
                    if (msg.xmlSummaryLabel.isNotBlank()) {
                        appendLine("Context: ${msg.xmlSummaryLabel}")
                    }
                    appendLine(msg.text.trim())
                    if (msg.matchedRefsCsv.isNotBlank()) {
                        appendLine("Matched Refs: ${msg.matchedRefsCsv}")
                    }
                    if (msg.xmlContent.isNotBlank()) {
                        appendLine("--- Generated XML Payload ---")
                        appendLine(msg.xmlContent.trim())
                    }
                    appendLine("------------------------------------------------------------")
                }
            }
        }
    }

    fun formatSmsThreadArchive(
        archive: ThreadWithAiArchive,
        format: ConversationExportFormat,
        isPersian: Boolean
    ): String {
        val thread = archive.thread
        val displayTitle = thread.contactName?.takeIf { it.isNotBlank() } ?: thread.address
        val primaryMode = if (isPersian) CalendarMode.JALALI else CalendarMode.GREGORIAN
        val sortedMsgs = archive.messages.sortedWith(compareBy({ it.timestamp }, { it.id }))

        return when (format) {
            ConversationExportFormat.JSON -> {
                val root = JSONObject().apply {
                    put("exportType", "sms_thread_archive")
                    put("exportedAtMs", System.currentTimeMillis())
                    put("exportedAtJalali", DateFormatter.formatJalali(System.currentTimeMillis(), usePersianDigits = false))
                    put("exportedAtGregorian", DateFormatter.formatGregorian(System.currentTimeMillis()))
                    put("thread", JSONObject().apply {
                        put("threadId", thread.threadId)
                        put("contactName", thread.contactName ?: JSONObject.NULL)
                        put("address", thread.address)
                        put("messageCount", sortedMsgs.size)
                        put("unreadCount", thread.unreadCount)
                        put("isPinned", thread.isPinned)
                    })
                    put("smsMessages", JSONArray().apply {
                        for (m in sortedMsgs) {
                            put(JSONObject().apply {
                                put("id", m.id)
                                put("direction", if (m.isOutgoing) "OUTGOING" else "INCOMING")
                                put("sender", m.sender)
                                put("address", m.address)
                                put("body", m.body)
                                put("timestampMs", m.timestamp)
                                put("timestampJalali", DateFormatter.formatJalali(m.timestamp, usePersianDigits = false))
                                put("timestampGregorian", DateFormatter.formatGregorian(m.timestamp))
                                put("deliveryStatus", m.deliveryStatus)
                                put("isPinned", m.isPinned)
                            })
                        }
                    })
                    put("linkedAiSessions", JSONArray().apply {
                        for (session in archive.aiSessions) {
                            put(JSONObject(formatAiChatTopic(session, ConversationExportFormat.JSON, isPersian)))
                        }
                    })
                }
                root.toString(2)
            }

            ConversationExportFormat.TEXT -> buildString {
                appendLine("============================================================")
                appendLine(if (isPersian) "آرشیو پیامک: $displayTitle (${thread.address})" else "SMS THREAD ARCHIVE: $displayTitle (${thread.address})")
                appendLine("Messages: ${sortedMsgs.size} | Exported: ${DateFormatter.formatDualTimestamp(System.currentTimeMillis(), primaryMode, isPersian)}")
                appendLine("============================================================")
                appendLine()
                for (m in sortedMsgs) {
                    val dirLabel = if (m.isOutgoing) {
                        if (isPersian) "ارسالی" else "OUTGOING"
                    } else {
                        if (isPersian) "دریافتی" else "INCOMING"
                    }
                    val ts = DateFormatter.formatDualTimestamp(m.timestamp, primaryMode, usePersianDigits = isPersian)
                    val pinTag = if (m.isPinned) " [PINNED]" else ""
                    appendLine("[$dirLabel$pinTag] $ts")
                    appendLine(m.body)
                    appendLine()
                }
                if (archive.aiSessions.isNotEmpty()) {
                    appendLine("============================================================")
                    appendLine(if (isPersian) "سابقه چت‌های AI مرتبط با این گفتگو" else "LINKED AI CHAT SESSIONS")
                    appendLine("============================================================")
                    for (session in archive.aiSessions) {
                        appendLine(formatAiChatTopic(session, ConversationExportFormat.TEXT, isPersian))
                    }
                }
            }
        }
    }

    fun formatSearchArchiveEntry(
        entry: SearchHistoryEntity,
        results: List<SearchResultItem>,
        format: ConversationExportFormat,
        isPersian: Boolean
    ): String {
        val primaryMode = if (isPersian) CalendarMode.JALALI else CalendarMode.GREGORIAN
        return when (format) {
            ConversationExportFormat.JSON -> {
                JSONObject().apply {
                    put("exportType", "search_archive_entry")
                    put("query", entry.query)
                    put("origin", entry.origin)
                    put("resultCount", results.size)
                    put("timestampMs", entry.timestamp)
                    put("timestampJalali", DateFormatter.formatJalali(entry.timestamp, usePersianDigits = false))
                    put("timestampGregorian", DateFormatter.formatGregorian(entry.timestamp))
                    put("results", JSONArray().apply {
                        for (r in results) {
                            put(JSONObject().apply {
                                put("refId", r.refId)
                                put("source", r.source.name)
                                put("senderDisplayName", r.senderDisplayName)
                                put("senderSecondary", r.senderSecondary)
                                put("body", r.body)
                                put("timestampJalali", DateFormatter.formatJalali(r.timestamp, usePersianDigits = false))
                                put("timestampGregorian", DateFormatter.formatGregorian(r.timestamp))
                            })
                        }
                    })
                }.toString(2)
            }

            ConversationExportFormat.TEXT -> buildString {
                appendLine("============================================================")
                appendLine("SEARCH ARCHIVE: ${entry.query}")
                appendLine("Date: ${DateFormatter.formatDualTimestamp(entry.timestamp, primaryMode, usePersianDigits = isPersian)}")
                appendLine("Results (${results.size}):")
                appendLine("============================================================")
                for (r in results) {
                    val ts = DateFormatter.formatDualTimestamp(r.timestamp, primaryMode, usePersianDigits = isPersian)
                    appendLine("- [${r.source.name}] ${r.senderDisplayName} ($ts)")
                    appendLine("  ${r.body}")
                    appendLine()
                }
            }
        }
    }
}
