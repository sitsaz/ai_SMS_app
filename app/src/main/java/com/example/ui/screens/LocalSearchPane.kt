package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalCalendarMode
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.local.SearchDirectionFilter
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SearchSortOrder
import com.example.data.local.SearchSourceFilter
import com.example.data.search.SearchResultItem
import com.example.ui.components.BilingualDateRangeDialog
import com.example.ui.components.SearchResultCard
import com.example.ui.viewmodel.LocalSearchFilterState
import kotlinx.coroutines.delay

@Composable
fun LocalSearchPane(
    filterState: LocalSearchFilterState,
    results: List<SearchResultItem>,
    recentSearches: List<SearchHistoryEntity> = emptyList(),
    searchSavedTick: Long = 0L,
    onQueryChange: (String) -> Unit,
    onSortChange: (SearchSortOrder) -> Unit,
    onSourceChange: (SearchSourceFilter) -> Unit,
    onDirectionChange: (SearchDirectionFilter) -> Unit,
    onDateRangeChange: (Long, Long) -> Unit,
    onClearDateRange: () -> Unit,
    onSaveSearch: () -> Unit = {},
    onSelectRecentSearch: (SearchHistoryEntity) -> Unit = {},
    onResultClick: (SearchResultItem) -> Unit
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    val calendarMode = LocalCalendarMode.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showDateDialog by remember { mutableStateOf(false) }
    var showSavedBadge by remember { mutableStateOf(false) }

    LaunchedEffect(searchSavedTick) {
        if (searchSavedTick > 0L) {
            showSavedBadge = true
            delay(1_800L)
            showSavedBadge = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filterState.query,
            onValueChange = onQueryChange,
            placeholder = { Text(strings.searchPlaceholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (filterState.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = strings.closeSearch)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSaveSearch()
                    keyboardController?.hide()
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("local_search_input")
        )

        Spacer(Modifier.height(8.dp))

        if (recentSearches.isNotEmpty() && filterState.query.isBlank() && filterState.startMs == 0L) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = strings.recentSearchesLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                for (recent in recentSearches.take(8)) {
                    val labelText = recent.query.ifBlank { strings.filterDateRange }
                    AssistChip(
                        onClick = { onSelectRecentSearch(recent) },
                        label = {
                            Text(
                                text = "$labelText (${DateUtils.formatNumber(recent.resultCount, isPersian)})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("recent_search_chip_${recent.id}")
                    )
                }
            }
        }

        // Sort + Source + Direction + Date Range filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            FilterChip(
                selected = filterState.sortOrder == SearchSortOrder.NEWEST_FIRST,
                onClick = { onSortChange(SearchSortOrder.NEWEST_FIRST) },
                label = { Text(strings.sortNewest) },
                modifier = Modifier.testTag("sort_newest_chip")
            )
            FilterChip(
                selected = filterState.sortOrder == SearchSortOrder.OLDEST_FIRST,
                onClick = { onSortChange(SearchSortOrder.OLDEST_FIRST) },
                label = { Text(strings.sortOldest) },
                modifier = Modifier.testTag("sort_oldest_chip")
            )
            FilterChip(
                selected = filterState.sortOrder == SearchSortOrder.SENDER_ASC,
                onClick = { onSortChange(SearchSortOrder.SENDER_ASC) },
                label = { Text(strings.sortSenderAsc) },
                modifier = Modifier.testTag("sort_sender_chip")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = filterState.sourceFilter == SearchSourceFilter.ALL,
                onClick = { onSourceChange(SearchSourceFilter.ALL) },
                label = { Text(strings.filterAll) }
            )
            FilterChip(
                selected = filterState.sourceFilter == SearchSourceFilter.SMS_ONLY,
                onClick = { onSourceChange(SearchSourceFilter.SMS_ONLY) },
                label = { Text(strings.filterSmsOnly) }
            )
            FilterChip(
                selected = filterState.sourceFilter == SearchSourceFilter.NOTIFICATIONS_ONLY,
                onClick = { onSourceChange(SearchSourceFilter.NOTIFICATIONS_ONLY) },
                label = { Text(strings.filterNotificationsOnly) }
            )
            FilterChip(
                selected = filterState.directionFilter == SearchDirectionFilter.INCOMING,
                onClick = {
                    val next = if (filterState.directionFilter == SearchDirectionFilter.INCOMING) {
                        SearchDirectionFilter.ALL
                    } else {
                        SearchDirectionFilter.INCOMING
                    }
                    onDirectionChange(next)
                },
                label = { Text(strings.filterIncoming) }
            )
            FilterChip(
                selected = filterState.directionFilter == SearchDirectionFilter.OUTGOING,
                onClick = {
                    val next = if (filterState.directionFilter == SearchDirectionFilter.OUTGOING) {
                        SearchDirectionFilter.ALL
                    } else {
                        SearchDirectionFilter.OUTGOING
                    }
                    onDirectionChange(next)
                },
                label = { Text(strings.filterOutgoing) }
            )
            FilterChip(
                selected = filterState.startMs > 0L,
                onClick = { showDateDialog = true },
                leadingIcon = {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    if (filterState.startMs > 0L) {
                        val s = DateUtils.formatDateShort(filterState.startMs, calendarMode, usePersianDigits = isPersian)
                        val e = DateUtils.formatDateShort(filterState.endMs, calendarMode, usePersianDigits = isPersian)
                        Text("$s – $e")
                    } else {
                        Text(strings.filterDateRange)
                    }
                }
            )
            if (filterState.startMs > 0L) {
                FilterChip(
                    selected = false,
                    onClick = onClearDateRange,
                    label = { Text(strings.clearDateFilter) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${DateUtils.formatNumber(results.size, isPersian)} ${strings.resultsCountLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (filterState.query.isNotBlank() || filterState.startMs > 0L) {
                AssistChip(
                    onClick = onSaveSearch,
                    leadingIcon = {
                        Icon(
                            imageVector = if (showSavedBadge) Icons.Default.Check else Icons.Default.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (showSavedBadge) strings.searchSavedToast else strings.saveSearchAction,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("save_search_archive_button")
                )
            }
        }

        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(strings.noSearchResultsTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        strings.noSearchResultsBody,
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
                items(results, key = { it.refId }) { item ->
                    SearchResultCard(
                        item = item,
                        highlightQuery = filterState.query,
                        isPersian = isPersian,
                        strings = strings,
                        onClick = {
                            if (filterState.query.isNotBlank() || filterState.startMs > 0L) {
                                onSaveSearch()
                            }
                            onResultClick(item)
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showDateDialog) {
        BilingualDateRangeDialog(
            isPersian = isPersian,
            initialStartMs = filterState.startMs,
            initialEndMs = filterState.endMs,
            strings = strings,
            onDismiss = { showDateDialog = false },
            onConfirm = { s, e ->
                showDateDialog = false
                onDateRangeChange(s, e)
            }
        )
    }
}
