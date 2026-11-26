package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppStrings
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalCalendarMode
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.local.AiTopicEntity
import com.example.data.local.SearchDirectionFilter
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SearchSourceFilter
import com.example.data.search.SearchResultItem
import com.example.ui.components.MarkdownText
import com.example.ui.components.SearchResultCard
import com.example.ui.viewmodel.AiChatArchiveEntry
import com.example.ui.viewmodel.AiChatDisplayMessage

private enum class ArchiveFilterMode {
    ALL,
    SEARCHES,
    AI_CHATS
}

private sealed interface UnifiedArchiveCardItem {
    val sortTimestamp: Long
    val key: String

    data class SearchItem(val entry: SearchHistoryEntity) : UnifiedArchiveCardItem {
        override val sortTimestamp: Long = entry.timestamp
        override val key: String = "search_${entry.id}"
    }

    data class AiChatItem(val entry: AiChatArchiveEntry) : UnifiedArchiveCardItem {
        override val sortTimestamp: Long = entry.topic.updatedAt
        override val key: String = "ai_${entry.topic.id}"
    }
}

@Composable
fun SearchAndAiArchivePane(
    searchHistory: List<SearchHistoryEntity>,
    aiChatArchive: List<AiChatArchiveEntry>,
    selectedArchivedSearch: Pair<SearchHistoryEntity, List<SearchResultItem>>?,
    selectedArchivedAiChat: Pair<AiTopicEntity, List<AiChatDisplayMessage>>?,
    onOpenArchivedSearch: (SearchHistoryEntity) -> Unit,
    onDismissArchivedSearch: () -> Unit,
    onRerunSearch: (SearchHistoryEntity) -> Unit,
    onDeleteArchivedSearch: (Long) -> Unit,
    onClearSearchHistory: () -> Unit,
    onOpenArchivedAiChat: (Long) -> Unit,
    onDismissArchivedAiChat: () -> Unit,
    onContinueAiChat: (AiTopicEntity) -> Unit,
    onDeleteAiChatTopic: (Long) -> Unit,
    onShowExistingXml: (String, String, Int) -> Unit,
    onResultClick: (SearchResultItem) -> Unit
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    val calendarMode = LocalCalendarMode.current
    var filterMode by rememberSaveable { mutableStateOf(ArchiveFilterMode.ALL) }
    var filterQuery by rememberSaveable { mutableStateOf("") }

    val cleanQuery = filterQuery.trim().lowercase()

    val filteredSearches = remember(searchHistory, cleanQuery) {
        if (cleanQuery.isEmpty()) searchHistory
        else searchHistory.filter {
            it.query.lowercase().contains(cleanQuery) ||
                it.resultsSnapshotJson.lowercase().contains(cleanQuery)
        }
    }

    val filteredAiChats = remember(aiChatArchive, cleanQuery) {
        if (cleanQuery.isEmpty()) aiChatArchive
        else aiChatArchive.filter {
            it.topic.title.lowercase().contains(cleanQuery) ||
                it.firstUserPrompt.lowercase().contains(cleanQuery) ||
                it.latestAnswerPreview.lowercase().contains(cleanQuery) ||
                it.parsedSummary.lowercase().contains(cleanQuery)
        }
    }

    val visibleItems = remember(filterMode, filteredSearches, filteredAiChats) {
        when (filterMode) {
            ArchiveFilterMode.ALL -> {
                (filteredSearches.map { UnifiedArchiveCardItem.SearchItem(it) } +
                    filteredAiChats.map { UnifiedArchiveCardItem.AiChatItem(it) })
                    .sortedByDescending { it.sortTimestamp }
            }
            ArchiveFilterMode.SEARCHES -> {
                filteredSearches.map { UnifiedArchiveCardItem.SearchItem(it) }
            }
            ArchiveFilterMode.AI_CHATS -> {
                filteredAiChats.map { UnifiedArchiveCardItem.AiChatItem(it) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            placeholder = { Text(strings.archiveSearchHint) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (filterQuery.isNotEmpty()) {
                    IconButton(onClick = { filterQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = strings.closeSearch)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("archive_search_input")
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = filterMode == ArchiveFilterMode.ALL,
                onClick = { filterMode = ArchiveFilterMode.ALL },
                label = {
                    val total = searchHistory.size + aiChatArchive.size
                    Text("${strings.archiveFilterAll} (${DateUtils.formatNumber(total, isPersian)})")
                },
                modifier = Modifier.testTag("archive_filter_all")
            )
            FilterChip(
                selected = filterMode == ArchiveFilterMode.SEARCHES,
                onClick = { filterMode = ArchiveFilterMode.SEARCHES },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = {
                    Text("${strings.archiveFilterSearches} (${DateUtils.formatNumber(searchHistory.size, isPersian)})")
                },
                modifier = Modifier.testTag("archive_filter_searches")
            )
            FilterChip(
                selected = filterMode == ArchiveFilterMode.AI_CHATS,
                onClick = { filterMode = ArchiveFilterMode.AI_CHATS },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = {
                    Text("${strings.archiveFilterAiChats} (${DateUtils.formatNumber(aiChatArchive.size, isPersian)})")
                },
                modifier = Modifier.testTag("archive_filter_ai")
            )
            if (searchHistory.isNotEmpty() && filterMode != ArchiveFilterMode.AI_CHATS) {
                AssistChip(
                    onClick = onClearSearchHistory,
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(strings.clearSearchHistory) },
                    modifier = Modifier.testTag("archive_clear_searches_button")
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        if (visibleItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(strings.noArchiveItemsTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        strings.noArchiveItemsBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleItems, key = { it.key }) { item ->
                    when (item) {
                        is UnifiedArchiveCardItem.SearchItem -> {
                            ArchivedSearchCard(
                                entry = item.entry,
                                isPersian = isPersian,
                                calendarMode = calendarMode,
                                strings = strings,
                                onOpen = { onOpenArchivedSearch(item.entry) },
                                onRerun = { onRerunSearch(item.entry) },
                                onDelete = { onDeleteArchivedSearch(item.entry.id) }
                            )
                        }
                        is UnifiedArchiveCardItem.AiChatItem -> {
                            ArchivedAiChatCard(
                                entry = item.entry,
                                isPersian = isPersian,
                                calendarMode = calendarMode,
                                strings = strings,
                                onOpenDetails = { onOpenArchivedAiChat(item.entry.topic.id) },
                                onContinueChat = { onContinueAiChat(item.entry.topic) },
                                onDelete = { onDeleteAiChatTopic(item.entry.topic.id) }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    selectedArchivedSearch?.let { (entry, results) ->
        ArchivedSearchResultsSheet(
            entry = entry,
            results = results,
            isPersian = isPersian,
            calendarMode = calendarMode,
            strings = strings,
            onDismiss = onDismissArchivedSearch,
            onRerun = { onRerunSearch(entry) },
            onResultClick = { clicked ->
                onDismissArchivedSearch()
                onResultClick(clicked)
            }
        )
    }

    selectedArchivedAiChat?.let { (topic, msgs) ->
        ArchivedAiChatSheet(
            topic = topic,
            messages = msgs,
            isPersian = isPersian,
            calendarMode = calendarMode,
            strings = strings,
            onDismiss = onDismissArchivedAiChat,
            onContinueChat = {
                onDismissArchivedAiChat()
                onContinueAiChat(topic)
            },
            onShowExistingXml = onShowExistingXml,
            onMatchedItemClick = { clicked ->
                onDismissArchivedAiChat()
                onResultClick(clicked)
            }
        )
    }
}

@Composable
private fun ArchivedSearchCard(
    entry: SearchHistoryEntity,
    isPersian: Boolean,
    calendarMode: com.example.core.localization.CalendarMode,
    strings: AppStrings,
    onOpen: () -> Unit,
    onRerun: () -> Unit,
    onDelete: () -> Unit
) {
    val title = entry.query.ifBlank { strings.filterDateRange }
    val originLabel = if (entry.origin == "SMS_BAR") strings.originSmsBar else strings.originSearchTab
    val metaLine = buildSearchFilterSummary(entry, isPersian, calendarMode, strings)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("archived_search_card_${entry.id}")
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = originLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRerun,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("rerun_search_button_${entry.id}")
                    ) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = strings.rerunSearch,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_archived_search_${entry.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = strings.deleteMessage,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (metaLine.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${DateUtils.formatNumber(entry.resultCount, isPersian)} ${strings.archivedResultsCountLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = DateUtils.formatDateTime(entry.timestamp, calendarMode, usePersianDigits = isPersian),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArchivedAiChatCard(
    entry: AiChatArchiveEntry,
    isPersian: Boolean,
    calendarMode: com.example.core.localization.CalendarMode,
    strings: AppStrings,
    onOpenDetails: () -> Unit,
    onContinueChat: () -> Unit,
    onDelete: () -> Unit
) {
    val originLabel = if (entry.topic.originScreen == "AICHAT") {
        strings.originAiChatTab
    } else {
        strings.originAiTopicsTab
    }
    val previewText = entry.parsedSummary.ifBlank { entry.latestAnswerPreview.ifBlank { entry.firstUserPrompt } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("archived_ai_card_${entry.topic.id}")
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = originLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = entry.topic.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onContinueChat,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("continue_ai_chat_${entry.topic.id}")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = strings.continueAiChat,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_archived_ai_${entry.topic.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = strings.deleteTopic,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (previewText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${DateUtils.formatNumber(entry.messageCount, isPersian)} ${strings.messagesCountLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (entry.matchedRefsCount > 0) {
                        Text(
                            text = "${DateUtils.formatNumber(entry.matchedRefsCount, isPersian)} ${strings.matchedItemsBadge}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text(
                    text = DateUtils.formatDateTime(entry.topic.updatedAt, calendarMode, usePersianDigits = isPersian),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedSearchResultsSheet(
    entry: SearchHistoryEntity,
    results: List<SearchResultItem>,
    isPersian: Boolean,
    calendarMode: com.example.core.localization.CalendarMode,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onRerun: () -> Unit,
    onResultClick: (SearchResultItem) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.query.ifBlank { strings.filterDateRange },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${strings.archivedSnapshotNote} • ${DateUtils.formatDateTime(entry.timestamp, calendarMode, usePersianDigits = isPersian)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onRerun,
                    modifier = Modifier.testTag("sheet_rerun_search_button")
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rerunSearch)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noSearchResultsTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(results, key = { it.refId }) { item ->
                        SearchResultCard(
                            item = item,
                            highlightQuery = entry.query,
                            isPersian = isPersian,
                            calendarMode = calendarMode,
                            strings = strings,
                            onClick = { onResultClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedAiChatSheet(
    topic: AiTopicEntity,
    messages: List<AiChatDisplayMessage>,
    isPersian: Boolean,
    calendarMode: com.example.core.localization.CalendarMode,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onContinueChat: () -> Unit,
    onShowExistingXml: (String, String, Int) -> Unit,
    onMatchedItemClick: (SearchResultItem) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copiedMsgId by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = DateUtils.formatDateTime(topic.updatedAt, calendarMode, usePersianDigits = isPersian),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onContinueChat,
                    modifier = Modifier.testTag("sheet_continue_ai_chat_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.continueAiChat)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.entity.id }) { item ->
                    val msg = item.entity
                    val isUser = msg.role == "USER"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when {
                                msg.isError -> MaterialTheme.colorScheme.errorContainer
                                isUser -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier.fillMaxWidth(0.95f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (msg.isError) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("Error", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                                if (!isUser && !msg.isError && item.parsedResponse?.summary?.isNotBlank() == true) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = "${strings.aiSummaryLabel}: ${item.parsedResponse.summary}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                val textColor = when {
                                    msg.isError -> MaterialTheme.colorScheme.onErrorContainer
                                    isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                if (!isUser && !msg.isError) {
                                    MarkdownText(
                                        markdown = msg.text,
                                        textColor = textColor,
                                        baseStyle = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    SelectionContainer {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor
                                        )
                                    }
                                }

                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (msg.xmlSummaryLabel.isNotBlank()) {
                                        AssistChip(
                                            onClick = {
                                                if (msg.xmlContent.isNotBlank()) {
                                                    onShowExistingXml(
                                                        msg.xmlContent,
                                                        msg.xmlSummaryLabel,
                                                        msg.xmlContent.toByteArray(Charsets.UTF_8).size
                                                    )
                                                }
                                            },
                                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                            label = {
                                                Text(
                                                    msg.xmlSummaryLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    } else {
                                        Text(
                                            text = DateUtils.formatDateTime(msg.timestamp, calendarMode, usePersianDigits = isPersian),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textColor.copy(alpha = 0.65f)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val fullCopy = if (!isUser && !msg.isError && item.parsedResponse?.summary?.isNotBlank() == true) {
                                                "${item.parsedResponse.summary}\n\n${msg.text}"
                                            } else {
                                                msg.text
                                            }
                                            clipboard.setText(AnnotatedString(fullCopy))
                                            copiedMsgId = msg.id
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (copiedMsgId == msg.id) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = strings.copyMessage,
                                            tint = textColor.copy(alpha = 0.72f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (item.matchedItems.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            val reasonMap = item.parsedResponse?.matches
                                ?.associate { it.refId to it.relevanceReason }
                                .orEmpty()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (matched in item.matchedItems) {
                                    val reason = reasonMap[matched.refId].orEmpty()
                                    if (reason.isNotBlank()) {
                                        Text(
                                            text = "${strings.aiMatchReasonPrefix}: $reason",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                    }
                                    SearchResultCard(
                                        item = matched,
                                        highlightQuery = "",
                                        isPersian = isPersian,
                                        calendarMode = calendarMode,
                                        strings = strings,
                                        onClick = { onMatchedItemClick(matched) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildSearchFilterSummary(
    entry: SearchHistoryEntity,
    isPersian: Boolean,
    calendarMode: com.example.core.localization.CalendarMode,
    strings: AppStrings
): String {
    val parts = mutableListOf<String>()
    when (runCatching { SearchSourceFilter.valueOf(entry.sourceFilter) }.getOrDefault(SearchSourceFilter.ALL)) {
        SearchSourceFilter.ALL -> Unit
        SearchSourceFilter.SMS_ONLY -> parts.add(strings.filterSmsOnly)
        SearchSourceFilter.NOTIFICATIONS_ONLY -> parts.add(strings.filterNotificationsOnly)
    }
    when (runCatching { SearchDirectionFilter.valueOf(entry.directionFilter) }.getOrDefault(SearchDirectionFilter.ALL)) {
        SearchDirectionFilter.ALL -> Unit
        SearchDirectionFilter.INCOMING -> parts.add(strings.filterIncoming)
        SearchDirectionFilter.OUTGOING -> parts.add(strings.filterOutgoing)
    }
    if (entry.dateStartMs > 0L) {
        val s = DateUtils.formatDateShort(entry.dateStartMs, calendarMode, usePersianDigits = isPersian)
        val e = DateUtils.formatDateShort(entry.dateEndMs, calendarMode, usePersianDigits = isPersian)
        parts.add("$s – $e")
    }
    return parts.joinToString(" • ")
}
