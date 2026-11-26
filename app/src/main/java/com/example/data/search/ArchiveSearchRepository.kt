package com.example.data.search

import com.example.data.local.DeliveryStatus
import com.example.data.local.NotificationDao
import com.example.data.local.NotificationItem
import com.example.data.local.SearchDirectionFilter
import com.example.data.local.SearchHistoryDao
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SearchSortOrder
import com.example.data.local.SearchSourceFilter
import com.example.data.local.SmsDao
import com.example.data.local.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class ArchiveItemSource {
    SMS,
    NOTIFICATION
}

data class SearchResultItem(
    val refId: String,
    val source: ArchiveItemSource,
    val itemId: Long,
    val threadId: Long?,
    val senderDisplayName: String,
    val senderSecondary: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val deliveryStatus: DeliveryStatus
)

@Serializable
data class SearchResultSnapshot(
    val refId: String,
    val source: String,
    val itemId: Long,
    val threadId: Long? = null,
    val senderDisplayName: String,
    val senderSecondary: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean = false,
    val deliveryStatus: String = DeliveryStatus.NONE.name
)

class ArchiveSearchRepository(
    private val smsDao: SmsDao,
    private val notificationDao: NotificationDao,
    private val searchHistoryDao: SearchHistoryDao
) {
    companion object {
        private val snapshotJson = Json { ignoreUnknownKeys = true }
        private const val MAX_SNAPSHOT_ITEMS = 120

        fun encodeSnapshots(items: List<SearchResultItem>): String {
            if (items.isEmpty()) return "[]"
            val snapshots = items.take(MAX_SNAPSHOT_ITEMS).map { item ->
                SearchResultSnapshot(
                    refId = item.refId,
                    source = item.source.name,
                    itemId = item.itemId,
                    threadId = item.threadId,
                    senderDisplayName = item.senderDisplayName,
                    senderSecondary = item.senderSecondary,
                    body = item.body,
                    timestamp = item.timestamp,
                    isOutgoing = item.isOutgoing,
                    deliveryStatus = item.deliveryStatus.name
                )
            }
            return runCatching {
                snapshotJson.encodeToString(ListSerializer(SearchResultSnapshot.serializer()), snapshots)
            }.getOrDefault("[]")
        }

        fun decodeSnapshots(rawJson: String): List<SearchResultItem> {
            if (rawJson.isBlank()) return emptyList()
            val list = runCatching {
                snapshotJson.decodeFromString(ListSerializer(SearchResultSnapshot.serializer()), rawJson)
            }.getOrDefault(emptyList())
            return list.map { snap ->
                val src = runCatching { ArchiveItemSource.valueOf(snap.source) }
                    .getOrDefault(ArchiveItemSource.SMS)
                val status = runCatching { DeliveryStatus.valueOf(snap.deliveryStatus) }
                    .getOrDefault(DeliveryStatus.NONE)
                SearchResultItem(
                    refId = snap.refId,
                    source = src,
                    itemId = snap.itemId,
                    threadId = snap.threadId,
                    senderDisplayName = snap.senderDisplayName,
                    senderSecondary = snap.senderSecondary,
                    body = snap.body,
                    timestamp = snap.timestamp,
                    isOutgoing = snap.isOutgoing,
                    deliveryStatus = status
                )
            }
        }
    }

    fun searchUnified(
        query: String,
        sourceFilter: SearchSourceFilter,
        directionFilter: SearchDirectionFilter,
        sortOrder: SearchSortOrder,
        startMs: Long,
        endMs: Long
    ): Flow<List<SearchResultItem>> {
        val cleanQuery = query.trim()
        val ftsQuery = buildFtsPrefixQuery(cleanQuery)

        val smsFlow = if (ftsQuery != null) {
            combine(
                smsDao.searchSmsFts(ftsQuery, startMs, endMs),
                smsDao.searchSms(cleanQuery, startMs, endMs)
            ) { ftsHits, likeHits ->
                (ftsHits + likeHits).distinctBy { it.id }
            }
        } else {
            smsDao.searchSms(cleanQuery, startMs, endMs)
        }

        val notifFlow = if (ftsQuery != null) {
            combine(
                notificationDao.searchNotificationsFts(ftsQuery, startMs, endMs),
                notificationDao.searchNotifications(cleanQuery, startMs, endMs)
            ) { ftsHits, likeHits ->
                (ftsHits + likeHits).distinctBy { it.id }
            }
        } else {
            notificationDao.searchNotifications(cleanQuery, startMs, endMs)
        }

        return combine(smsFlow, notifFlow) { smsList, notifList ->
            val combined = ArrayList<SearchResultItem>(smsList.size + notifList.size)

            if (sourceFilter == SearchSourceFilter.ALL || sourceFilter == SearchSourceFilter.SMS_ONLY) {
                for (sms in smsList) {
                    val matchesDirection = when (directionFilter) {
                        SearchDirectionFilter.ALL -> true
                        SearchDirectionFilter.INCOMING -> !sms.isOutgoing
                        SearchDirectionFilter.OUTGOING -> sms.isOutgoing
                    }
                    if (matchesDirection) {
                        combined.add(sms.toSearchResult())
                    }
                }
            }

            if (sourceFilter == SearchSourceFilter.ALL || sourceFilter == SearchSourceFilter.NOTIFICATIONS_ONLY) {
                if (directionFilter != SearchDirectionFilter.OUTGOING) {
                    for (notif in notifList) {
                        combined.add(notif.toSearchResult())
                    }
                }
            }

            when (sortOrder) {
                SearchSortOrder.NEWEST_FIRST -> combined.sortByDescending { it.timestamp }
                SearchSortOrder.OLDEST_FIRST -> combined.sortBy { it.timestamp }
                SearchSortOrder.SENDER_ASC -> combined.sortWith(
                    compareBy<SearchResultItem> { it.senderDisplayName.lowercase() }
                        .thenByDescending { it.timestamp }
                )
            }
            combined
        }
    }

    suspend fun hydrateRefs(refs: List<String>): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (refs.isEmpty()) return@withContext emptyList()
        val smsIds = mutableListOf<Long>()
        val notifIds = mutableListOf<Long>()

        for (ref in refs) {
            val trimmed = ref.trim()
            when {
                trimmed.startsWith("sms:") -> trimmed.removePrefix("sms:").toLongOrNull()?.let(smsIds::add)
                trimmed.startsWith("notif:") -> trimmed.removePrefix("notif:").toLongOrNull()?.let(notifIds::add)
            }
        }

        val smsMap = if (smsIds.isNotEmpty()) smsDao.getByIds(smsIds).associateBy { "sms:${it.id}" } else emptyMap()
        val notifMap = if (notifIds.isNotEmpty()) notificationDao.getByIds(notifIds).associateBy { "notif:${it.id}" } else emptyMap()

        val ordered = mutableListOf<SearchResultItem>()
        val seen = HashSet<String>()
        for (ref in refs) {
            val key = ref.trim()
            if (!seen.add(key)) continue
            smsMap[key]?.let { ordered.add(it.toSearchResult()) }
            notifMap[key]?.let { ordered.add(it.toSearchResult()) }
        }
        ordered
    }

    private fun buildFtsPrefixQuery(raw: String): String? {
        if (raw.isBlank()) return null
        val tokens = raw.split(Regex("""\s+"""))
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>> =
        searchHistoryDao.observeAll()

    fun observeRecentSearchHistory(limit: Int = 15): Flow<List<SearchHistoryEntity>> =
        searchHistoryDao.observeRecent(limit)

    suspend fun saveSearchToArchive(
        query: String,
        origin: String = "UNIFIED",
        sourceFilter: SearchSourceFilter = SearchSourceFilter.ALL,
        directionFilter: SearchDirectionFilter = SearchDirectionFilter.ALL,
        sortOrder: SearchSortOrder = SearchSortOrder.NEWEST_FIRST,
        startMs: Long = 0L,
        endMs: Long = 0L,
        results: List<SearchResultItem>
    ): Long = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val now = System.currentTimeMillis()
        val refsCsv = results.take(MAX_SNAPSHOT_ITEMS).joinToString(",") { it.refId }
        val encoded = encodeSnapshots(results)

        val existing = searchHistoryDao.findMatchingEntry(
            query = cleanQuery,
            origin = origin,
            sourceFilter = sourceFilter.name,
            directionFilter = directionFilter.name,
            sortOrder = sortOrder.name,
            dateStartMs = startMs,
            dateEndMs = endMs
        )

        val entry = SearchHistoryEntity(
            id = existing?.id ?: 0L,
            query = cleanQuery,
            origin = origin,
            sourceFilter = sourceFilter.name,
            directionFilter = directionFilter.name,
            sortOrder = sortOrder.name,
            dateStartMs = startMs,
            dateEndMs = endMs,
            resultCount = results.size,
            matchedRefsCsv = refsCsv,
            resultsSnapshotJson = encoded,
            timestamp = now
        )
        searchHistoryDao.upsert(entry)
    }

    suspend fun loadArchivedSearchResults(entry: SearchHistoryEntity): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            val snapshotItems = decodeSnapshots(entry.resultsSnapshotJson)
            val refs = entry.matchedRefsCsv
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (refs.isEmpty()) return@withContext snapshotItems

            val liveByRef = hydrateRefs(refs).associateBy { it.refId }
            if (snapshotItems.isEmpty()) {
                return@withContext refs.mapNotNull { liveByRef[it] }
            }
            snapshotItems.map { snap -> liveByRef[snap.refId] ?: snap }
        }

    suspend fun deleteSearchHistory(id: Long) = withContext(Dispatchers.IO) {
        searchHistoryDao.deleteById(id)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchHistoryDao.clearAll()
    }
}

fun SmsMessage.toSearchResult(): SearchResultItem {
    val status = runCatching { DeliveryStatus.valueOf(deliveryStatus) }.getOrDefault(DeliveryStatus.NONE)
    return SearchResultItem(
        refId = "sms:$id",
        source = ArchiveItemSource.SMS,
        itemId = id,
        threadId = threadId,
        senderDisplayName = contactName?.takeIf { it.isNotBlank() } ?: address,
        senderSecondary = address,
        body = body,
        timestamp = timestamp,
        isOutgoing = isOutgoing,
        deliveryStatus = status
    )
}

fun NotificationItem.toSearchResult(): SearchResultItem {
    return SearchResultItem(
        refId = "notif:$id",
        source = ArchiveItemSource.NOTIFICATION,
        itemId = id,
        threadId = null,
        senderDisplayName = senderOrTitle,
        senderSecondary = appName,
        body = if (subText.isNotBlank()) "$body\n($subText)" else body,
        timestamp = timestamp,
        isOutgoing = false,
        deliveryStatus = DeliveryStatus.NONE
    )
}
