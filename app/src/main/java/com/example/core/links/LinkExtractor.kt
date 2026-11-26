package com.example.core.links

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

object LinkExtractor {

    private val urlRegex = Regex(
        """(?i)\b(?:https?://|intent://|tg://|whatsapp://|www\.)[^\s<>"'\]\)،؛؟«»]+"""
    )

    fun extractUrlsFromTexts(vararg texts: String?): List<String> {
        val found = linkedSetOf<String>()
        for (raw in texts) {
            if (raw.isNullOrBlank()) continue
            urlRegex.findAll(raw).forEach { match ->
                val cleaned = cleanUrl(match.value)
                if (cleaned.isNotBlank()) {
                    found.add(cleaned)
                }
            }
        }
        return found.toList()
    }

    fun extractUrlsFromNotification(
        notification: Notification,
        vararg fallbackTexts: String?
    ): List<String> {
        val collected = mutableListOf<String?>()
        collected.addAll(fallbackTexts)
        val extras: Bundle? = notification.extras
        if (extras != null) {
            for (key in extras.keySet()) {
                val value = extras.get(key)
                when (value) {
                    is CharSequence -> collected.add(value.toString())
                    is Array<*> -> value.forEach { item ->
                        if (item is CharSequence) collected.add(item.toString())
                    }
                    is Uri -> collected.add(value.toString())
                }
            }
        }
        return extractUrlsFromTexts(*collected.toTypedArray())
    }

    fun serializeUrls(urls: List<String>): String =
        urls.distinct().joinToString("\n")

    fun parseSerializedUrls(serialized: String, vararg extraTexts: String?): List<String> {
        val saved = serialized
            .lineSequence()
            .map { cleanUrl(it) }
            .filter { it.isNotBlank() }
            .toList()
        val fromTexts = extractUrlsFromTexts(*extraTexts)
        return (saved + fromTexts).distinct()
    }

    fun cleanUrl(raw: String): String {
        var u = raw.trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '،', '؛', '؟', '»', '«')
        if (u.startsWith("www.", ignoreCase = true)) {
            u = "https://$u"
        }
        return u
    }

    fun openUrlSafely(context: Context, rawUrl: String): Boolean {
        val normalized = cleanUrl(rawUrl)
        if (normalized.isBlank()) return false
        val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme !in setOf("http", "https", "tg", "whatsapp", "mailto", "tel")) {
            return false
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}

object LiveNotificationIntentCache {
    private val cache = ConcurrentHashMap<String, PendingIntent>()

    fun put(notificationKey: String, pendingIntent: PendingIntent?) {
        if (notificationKey.isBlank() || pendingIntent == null) return
        if (cache.size > 200) {
            cache.clear()
        }
        cache[notificationKey] = pendingIntent
    }

    fun hasIntent(notificationKey: String): Boolean =
        cache.containsKey(notificationKey)

    fun launch(notificationKey: String): Boolean {
        val pi = cache[notificationKey] ?: return false
        return runCatching {
            pi.send()
            true
        }.getOrDefault(false)
    }
}
