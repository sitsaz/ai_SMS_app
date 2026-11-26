package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.ai.XmlArchiveBuildResult
import com.example.data.archive.ConversationExportFormat
import com.example.data.local.AiTopicEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.search.SearchResultItem
import com.example.ui.components.XmlPreviewDialog
import com.example.ui.viewmodel.AiChatArchiveEntry
import com.example.ui.viewmodel.AiChatDisplayMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    searchHistory: List<SearchHistoryEntity>,
    aiChatArchive: List<AiChatArchiveEntry>,
    selectedArchivedSearch: Pair<SearchHistoryEntity, List<SearchResultItem>>?,
    selectedArchivedAiChat: Pair<AiTopicEntity, List<AiChatDisplayMessage>>?,
    xmlPreview: XmlArchiveBuildResult?,
    onBack: () -> Unit,
    onOpenArchivedSearch: (SearchHistoryEntity) -> Unit,
    onDismissArchivedSearch: () -> Unit,
    onRerunSearch: (SearchHistoryEntity) -> Unit,
    onDeleteArchivedSearch: (Long) -> Unit,
    onClearSearchHistory: () -> Unit,
    onOpenArchivedAiChat: (Long) -> Unit,
    onDismissArchivedAiChat: () -> Unit,
    onContinueAiChat: (AiTopicEntity) -> Unit,
    onDeleteAiChatTopic: (Long) -> Unit,
    onExportAiChat: (Long, ConversationExportFormat) -> Unit,
    onExportSearchEntry: (SearchHistoryEntity, ConversationExportFormat) -> Unit,
    onShowExistingXml: (String, String, Int) -> Unit,
    onDismissXmlPreview: () -> Unit,
    onResultClick: (SearchResultItem) -> Unit
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("archive_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                title = {
                    Text(
                        text = strings.archiveTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SearchAndAiArchivePane(
                searchHistory = searchHistory,
                aiChatArchive = aiChatArchive,
                selectedArchivedSearch = selectedArchivedSearch,
                selectedArchivedAiChat = selectedArchivedAiChat,
                onOpenArchivedSearch = onOpenArchivedSearch,
                onDismissArchivedSearch = onDismissArchivedSearch,
                onRerunSearch = onRerunSearch,
                onDeleteArchivedSearch = onDeleteArchivedSearch,
                onClearSearchHistory = onClearSearchHistory,
                onOpenArchivedAiChat = onOpenArchivedAiChat,
                onDismissArchivedAiChat = onDismissArchivedAiChat,
                onContinueAiChat = onContinueAiChat,
                onDeleteAiChatTopic = onDeleteAiChatTopic,
                onExportAiChat = onExportAiChat,
                onExportSearchEntry = onExportSearchEntry,
                onShowExistingXml = onShowExistingXml,
                onResultClick = onResultClick
            )
        }
    }

    if (xmlPreview != null) {
        XmlPreviewDialog(
            xmlResult = xmlPreview,
            isPersian = isPersian,
            strings = strings,
            onDismiss = onDismissXmlPreview
        )
    }
}
