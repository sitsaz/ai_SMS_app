package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.unit.dp
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.ai.XmlArchiveBuildResult
import com.example.data.local.AiTopicEntity
import com.example.data.local.SearchDirectionFilter
import com.example.data.local.SearchSortOrder
import com.example.data.local.SearchSourceFilter
import com.example.data.search.SearchResultItem
import com.example.ui.viewmodel.AiChatDisplayMessage
import com.example.ui.viewmodel.AiScopeDraft
import com.example.ui.viewmodel.LocalSearchFilterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndAiScreen(
    filterState: LocalSearchFilterState,
    localResults: List<SearchResultItem>,
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
    val isPersian = LocalIsPersian.current
    var modeIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.tabSearch,
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
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.testTag("mode_local_search_button")
                ) {
                    Text(strings.searchModeLocal)
                }
                SegmentedButton(
                    selected = modeIndex == 1,
                    onClick = { modeIndex = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    modifier = Modifier.testTag("mode_ai_topics_button")
                ) {
                    Text(strings.searchModeAiTopics)
                }
            }

            if (modeIndex == 0) {
                LocalSearchPane(
                    filterState = filterState,
                    results = localResults,
                    onQueryChange = onQueryChange,
                    onSortChange = onSortChange,
                    onSourceChange = onSourceChange,
                    onDirectionChange = onDirectionChange,
                    onDateRangeChange = onDateRangeChange,
                    onClearDateRange = onClearDateRange,
                    onResultClick = onSearchResultClick
                )
            } else {
                AiTopicsChatPane(
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
            }
        }
    }
}
