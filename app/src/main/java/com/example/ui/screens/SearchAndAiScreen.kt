package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.localization.LocalStrings
import com.example.data.ai.XmlArchiveBuildResult
import com.example.data.local.AiTopicEntity
import com.example.data.local.SearchDirectionFilter
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SearchSortOrder
import com.example.data.local.SearchSourceFilter
import com.example.data.search.SearchResultItem
import com.example.ui.viewmodel.AiChatArchiveEntry
import com.example.ui.viewmodel.AiChatDisplayMessage
import com.example.ui.viewmodel.AiScopeDraft
import com.example.ui.viewmodel.LocalSearchFilterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndAiScreen(
    filterState: LocalSearchFilterState,
    localResults: List<SearchResultItem>,
    recentSearches: List<SearchHistoryEntity> = emptyList(),
    searchHistory: List<SearchHistoryEntity> = emptyList(),
    aiChatArchive: List<AiChatArchiveEntry> = emptyList(),
    selectedArchivedSearch: Pair<SearchHistoryEntity, List<SearchResultItem>>? = null,
    selectedArchivedAiChat: Pair<AiTopicEntity, List<AiChatDisplayMessage>>? = null,
    searchSavedTick: Long = 0L,
    isAiConfigured: Boolean,
    topics: List<AiTopicEntity>,
    activeTopicId: Long?,
    activeTopic: AiTopicEntity?,
    chatMessages: List<AiChatDisplayMessage>,
    isAiSending: Boolean,
    showScopeSheet: Boolean,
    pendingQuestion: String?,
    scopeDraft: AiScopeDraft,
    draftCounts: Pair<Int, Int>,
    xmlPreview: XmlArchiveBuildResult?,
    onQueryChange: (String) -> Unit,
    onSortChange: (SearchSortOrder) -> Unit,
    onSourceChange: (SearchSourceFilter) -> Unit,
    onDirectionChange: (SearchDirectionFilter) -> Unit,
    onDateRangeChange: (Long, Long) -> Unit,
    onClearDateRange: () -> Unit,
    onSaveSearchNow: () -> Unit = {},
    onOpenArchivedSearch: (SearchHistoryEntity) -> Unit = {},
    onDismissArchivedSearch: () -> Unit = {},
    onRerunArchivedSearch: (SearchHistoryEntity) -> Unit = {},
    onDeleteArchivedSearch: (Long) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onOpenArchivedAiChat: (Long) -> Unit = {},
    onDismissArchivedAiChat: () -> Unit = {},
    onOpenTopicInAiChatScreen: (Long) -> Unit = {},
    onSelectTopic: (Long?) -> Unit,
    onNewTopic: () -> Unit,
    onDeleteTopic: (Long) -> Unit,
    onAskQuestion: (String) -> Unit,
    onOpenScopeEditor: () -> Unit,
    onDismissScopeSheet: () -> Unit,
    onUpdateScopeDraft: ((AiScopeDraft) -> AiScopeDraft) -> Unit,
    onPreviewXml: () -> Unit,
    onShowExistingXml: (String, String, Int) -> Unit,
    onDismissXmlPreview: () -> Unit,
    onConfirmScopeAndSend: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearchResultClick: (SearchResultItem) -> Unit
) {
    val strings = LocalStrings.current
    var modeIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (modeIndex == 2) strings.archiveTitle else strings.tabSearch,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                SegmentedButton(
                    selected = modeIndex == 0,
                    onClick = { modeIndex = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.testTag("mode_local_search_button")
                ) {
                    Text(strings.searchModeLocal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SegmentedButton(
                    selected = modeIndex == 1,
                    onClick = { modeIndex = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    modifier = Modifier.testTag("mode_ai_topics_button")
                ) {
                    Text(strings.searchModeAiTopics, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SegmentedButton(
                    selected = modeIndex == 2,
                    onClick = { modeIndex = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    modifier = Modifier.testTag("mode_archive_button")
                ) {
                    Text(strings.tabArchive, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            when (modeIndex) {
                0 -> LocalSearchPane(
                    filterState = filterState,
                    results = localResults,
                    recentSearches = recentSearches,
                    searchSavedTick = searchSavedTick,
                    onQueryChange = onQueryChange,
                    onSortChange = onSortChange,
                    onSourceChange = onSourceChange,
                    onDirectionChange = onDirectionChange,
                    onDateRangeChange = onDateRangeChange,
                    onClearDateRange = onClearDateRange,
                    onSaveSearch = onSaveSearchNow,
                    onSelectRecentSearch = { entry -> onOpenArchivedSearch(entry) },
                    onResultClick = onSearchResultClick
                )
                1 -> AiTopicsChatPane(
                    isAiConfigured = isAiConfigured,
                    topics = topics,
                    activeTopicId = activeTopicId,
                    activeTopic = activeTopic,
                    chatMessages = chatMessages,
                    isAiSending = isAiSending,
                    showScopeSheet = showScopeSheet,
                    pendingQuestion = pendingQuestion,
                    scopeDraft = scopeDraft,
                    draftCounts = draftCounts,
                    xmlPreview = xmlPreview,
                    onSelectTopic = onSelectTopic,
                    onNewTopic = onNewTopic,
                    onDeleteTopic = onDeleteTopic,
                    onAskQuestion = onAskQuestion,
                    onOpenScopeEditor = onOpenScopeEditor,
                    onDismissScopeSheet = onDismissScopeSheet,
                    onUpdateScopeDraft = onUpdateScopeDraft,
                    onPreviewXml = onPreviewXml,
                    onShowExistingXml = onShowExistingXml,
                    onDismissXmlPreview = onDismissXmlPreview,
                    onConfirmScopeAndSend = onConfirmScopeAndSend,
                    onOpenSettings = onOpenSettings,
                    onMatchedItemClick = onSearchResultClick
                )
                else -> SearchAndAiArchivePane(
                    searchHistory = searchHistory,
                    aiChatArchive = aiChatArchive,
                    selectedArchivedSearch = selectedArchivedSearch,
                    selectedArchivedAiChat = selectedArchivedAiChat,
                    onOpenArchivedSearch = onOpenArchivedSearch,
                    onDismissArchivedSearch = onDismissArchivedSearch,
                    onRerunSearch = { entry ->
                        onRerunArchivedSearch(entry)
                        modeIndex = 0
                    },
                    onDeleteArchivedSearch = onDeleteArchivedSearch,
                    onClearSearchHistory = onClearSearchHistory,
                    onOpenArchivedAiChat = onOpenArchivedAiChat,
                    onDismissArchivedAiChat = onDismissArchivedAiChat,
                    onContinueAiChat = { topic ->
                        if (topic.originScreen == "AICHAT") {
                            onOpenTopicInAiChatScreen(topic.id)
                        } else {
                            onSelectTopic(topic.id)
                            modeIndex = 1
                        }
                    },
                    onDeleteAiChatTopic = onDeleteTopic,
                    onShowExistingXml = onShowExistingXml,
                    onResultClick = onSearchResultClick
                )
            }
        }
    }

    if (modeIndex == 0 && selectedArchivedSearch != null) {
        // Ensure clicking a recent search chip in LocalSearchPane displays the saved snapshot sheet
        SearchAndAiArchivePane(
            searchHistory = emptyList(),
            aiChatArchive = emptyList(),
            selectedArchivedSearch = selectedArchivedSearch,
            selectedArchivedAiChat = null,
            onOpenArchivedSearch = {},
            onDismissArchivedSearch = onDismissArchivedSearch,
            onRerunSearch = { entry ->
                onRerunArchivedSearch(entry)
                modeIndex = 0
            },
            onDeleteArchivedSearch = {},
            onClearSearchHistory = {},
            onOpenArchivedAiChat = {},
            onDismissArchivedAiChat = {},
            onContinueAiChat = {},
            onDeleteAiChatTopic = {},
            onShowExistingXml = onShowExistingXml,
            onResultClick = onSearchResultClick
        )
    }
}
