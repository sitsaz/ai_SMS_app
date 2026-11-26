package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.localization.DateFormatter
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalCalendarMode
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.ai.XmlArchiveBuildResult
import com.example.data.archive.ConversationExportFormat
import com.example.data.local.AiChatContextSource
import com.example.data.local.ArchiveRangePreset
import com.example.data.local.ConversationRow
import com.example.data.search.SearchResultItem
import com.example.ui.components.BilingualDateRangeDialog
import com.example.ui.components.MarkdownText
import com.example.ui.components.SearchResultCard
import com.example.ui.components.SenderAvatar
import com.example.ui.components.XmlPreviewDialog
import com.example.ui.viewmodel.AiChatArchiveSummary
import com.example.ui.viewmodel.AiChatContextConfig
import com.example.ui.viewmodel.AiChatDisplayMessage
import com.example.ui.viewmodel.NotificationAppFilterOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    isAiConfigured: Boolean,
    aiContextLimitTokens: Int = 32_000,
    aiSupportsFileUpload: Boolean = false,
    contextConfig: AiChatContextConfig,
    conversations: List<ConversationRow>,
    notificationApps: List<NotificationAppFilterOption>,
    totalNotificationCount: Int,
    messages: List<AiChatDisplayMessage>,
    archivedSessions: List<AiChatArchiveSummary> = emptyList(),
    activeTopicId: Long? = null,
    isSending: Boolean,
    xmlPreview: XmlArchiveBuildResult?,
    onSelectSource: (AiChatContextSource) -> Unit,
    onSelectThread: (Long) -> Unit,
    onSelectNotificationApp: (String?) -> Unit,
    onSelectRangePreset: (ArchiveRangePreset, Long, Long) -> Unit,
    onToggleSmartContext: (Boolean) -> Unit = {},
    onNewChat: () -> Unit,
    onLoadArchivedSession: (Long) -> Unit = {},
    onDeleteArchivedSession: (Long) -> Unit = {},
    onPreviewCurrentXml: (String) -> Unit,
    onShowStoredXml: (String, String) -> Unit,
    onDismissXmlPreview: () -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit = {},
    onExportChat: (Long, ConversationExportFormat) -> Unit = { _, _ -> },
    onMatchedItemClick: (SearchResultItem) -> Unit
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    val calendarMode = LocalCalendarMode.current
    val clipboard = LocalClipboardManager.current

    var draftInput by remember { mutableStateOf("") }
    var showThreadPicker by remember { mutableStateOf(false) }
    var showArchiveSheet by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var copiedMsgId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    val selectedThread = remember(conversations, contextConfig.selectedThreadId) {
        conversations.firstOrNull { it.threadId == contextConfig.selectedThreadId }
            ?: conversations.firstOrNull()
    }

    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = strings.aiChatTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.autoXmlPromptBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (activeTopicId != null && messages.isNotEmpty()) {
                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                modifier = Modifier.testTag("aichat_export_button")
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = strings.exportConversation)
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.exportAsText) },
                                    onClick = {
                                        showExportMenu = false
                                        onExportChat(activeTopicId, ConversationExportFormat.TEXT)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.exportAsJson) },
                                    onClick = {
                                        showExportMenu = false
                                        onExportChat(activeTopicId, ConversationExportFormat.JSON)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onOpenArchive,
                        modifier = Modifier.testTag("aichat_history_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = strings.openArchiveButton)
                    }
                    IconButton(
                        onClick = { onPreviewCurrentXml(draftInput) },
                        modifier = Modifier.testTag("aichat_preview_xml_button")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = strings.previewXml)
                    }
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.testTag("aichat_new_chat_button")
                    ) {
                        Icon(Icons.Default.AddComment, contentDescription = strings.newChat)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Source selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = contextConfig.source == AiChatContextSource.SELECTED_SMS_THREAD,
                    onClick = { onSelectSource(AiChatContextSource.SELECTED_SMS_THREAD) },
                    leadingIcon = { Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(strings.sourceSmsThread) },
                    modifier = Modifier.testTag("aichat_source_thread_chip")
                )
                FilterChip(
                    selected = contextConfig.source == AiChatContextSource.NOTIFICATION_HISTORY,
                    onClick = { onSelectSource(AiChatContextSource.NOTIFICATION_HISTORY) },
                    leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(strings.sourceNotificationHistory) },
                    modifier = Modifier.testTag("aichat_source_notifications_chip")
                )
                FilterChip(
                    selected = contextConfig.source == AiChatContextSource.COMBINED_ARCHIVE,
                    onClick = { onSelectSource(AiChatContextSource.COMBINED_ARCHIVE) },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(strings.sourceCombinedArchive) },
                    modifier = Modifier.testTag("aichat_source_combined_chip")
                )
                FilterChip(
                    selected = contextConfig.smartContextEnabled,
                    onClick = { onToggleSmartContext(!contextConfig.smartContextEnabled) },
                    label = {
                        val ctxStr = DateUtils.formatNumber(aiContextLimitTokens, isPersian)
                        val suffix = if (contextConfig.smartContextEnabled && aiSupportsFileUpload) {
                            strings.smartFileUploadedBadge
                        } else {
                            "$ctxStr tok"
                        }
                        Text("${strings.contextSmartAuto} ($suffix)")
                    },
                    modifier = Modifier.testTag("aichat_smart_context_chip")
                )
            }

            // Active Context Card (Selected SMS Thread or Notification History Filter)
            when (contextConfig.source) {
                AiChatContextSource.SELECTED_SMS_THREAD -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { showThreadPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("aichat_context_picker_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val title = selectedThread?.let {
                                it.contactName?.takeIf { n -> n.isNotBlank() } ?: it.address
                            } ?: strings.noThreadSelected
                            SenderAvatar(label = title, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = selectedThread?.body ?: strings.selectSmsThreadHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = strings.selectSmsThreadTitle,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                AiChatContextSource.NOTIFICATION_HISTORY,
                AiChatContextSource.COMBINED_ARCHIVE -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = contextConfig.selectedAppPackage == null,
                            onClick = { onSelectNotificationApp(null) },
                            label = {
                                val countStr = DateUtils.formatNumber(totalNotificationCount, isPersian)
                                Text("${strings.allAppsFilter} ($countStr)")
                            },
                            modifier = Modifier.testTag("aichat_notif_all_apps_chip")
                        )
                        for (app in notificationApps.take(10)) {
                            FilterChip(
                                selected = contextConfig.selectedAppPackage == app.packageName,
                                onClick = { onSelectNotificationApp(app.packageName) },
                                label = {
                                    Text("${app.appName} (${DateUtils.formatNumber(app.count, isPersian)})")
                                }
                            )
                        }
                        listOf(
                            ArchiveRangePreset.TODAY to strings.presetToday,
                            ArchiveRangePreset.ONE_WEEK to strings.presetOneWeek,
                            ArchiveRangePreset.ONE_MONTH to strings.presetOneMonth,
                            ArchiveRangePreset.CUSTOM to strings.presetCustomRange
                        ).forEach { (preset, label) ->
                            FilterChip(
                                selected = contextConfig.preset == preset,
                                onClick = {
                                    if (preset == ArchiveRangePreset.CUSTOM) {
                                        showCustomDateDialog = true
                                    } else {
                                        onSelectRangePreset(preset, 0L, 0L)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            if (!isAiConfigured) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.aiNotConfiguredTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(strings.aiNotConfiguredBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onOpenSettings) {
                            Text(strings.openSettings)
                        }
                    }
                }
            }

            // Conversational Transcript
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !isSending) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(strings.aiChatEmptyTitle, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.aiChatEmptyBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        val suggestions = if (contextConfig.source == AiChatContextSource.NOTIFICATION_HISTORY) {
                            listOf(strings.quickSuggestNotifSummary, strings.quickSuggestNotifFinance)
                        } else {
                            listOf(strings.quickSuggestSummarize, strings.quickSuggestOtpAmounts, strings.quickSuggestDates)
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (q in suggestions) {
                                AssistChip(
                                    onClick = { if (isAiConfigured) onSendMessage(q) },
                                    label = { Text(q) }
                                )
                            }
                        }
                        if (archivedSessions.isNotEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.savedAiChatsLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                AssistChip(
                                    onClick = { showArchiveSheet = true },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(DateUtils.formatNumber(archivedSessions.size, isPersian)) }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (session in archivedSessions.take(3)) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        onClick = { onLoadArchivedSession(session.topic.id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("aichat_quick_archive_${session.topic.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = session.topic.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val sub = session.parsedSummary.ifBlank { session.latestAnswerPreview }
                                                if (sub.isNotBlank()) {
                                                    Text(
                                                        text = sub,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = DateFormatter.formatSmsTimestamp(
                                                    timestampMs = session.topic.updatedAt,
                                                    mode = calendarMode,
                                                    usePersianDigits = isPersian
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
                                    shape = RoundedCornerShape(18.dp),
                                    color = when {
                                        msg.isError -> MaterialTheme.colorScheme.errorContainer
                                        isUser -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    modifier = Modifier.widthIn(max = 340.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
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
                                        val bubbleTextColor = when {
                                            msg.isError -> MaterialTheme.colorScheme.onErrorContainer
                                            isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                        if (!isUser && !msg.isError) {
                                            MarkdownText(
                                                markdown = msg.text,
                                                textColor = bubbleTextColor,
                                                baseStyle = MaterialTheme.typography.bodyMedium
                                            )
                                        } else {
                                            SelectionContainer {
                                                Text(
                                                    text = msg.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = bubbleTextColor
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
                                                            onShowStoredXml(msg.xmlContent, msg.xmlSummaryLabel)
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
                                                    text = DateFormatter.formatChatTimestamp(
                                                        timestampMs = msg.timestamp,
                                                        mode = calendarMode,
                                                        usePersianDigits = isPersian
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = bubbleTextColor.copy(alpha = 0.65f)
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
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .testTag("copy_aichat_msg_${msg.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (copiedMsgId == msg.id) Icons.Default.Check else Icons.Default.ContentCopy,
                                                    contentDescription = strings.copyMessage,
                                                    tint = bubbleTextColor.copy(alpha = 0.72f),
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

                                val followUps = item.parsedResponse?.suggestedFollowUps.orEmpty()
                                if (!isUser && !msg.isError && followUps.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        for (q in followUps) {
                                            AssistChip(
                                                onClick = { if (!isSending && isAiConfigured) onSendMessage(q) },
                                                label = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isSending) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(strings.aiThinking, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Composer
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draftInput,
                        onValueChange = { draftInput = it },
                        placeholder = { Text(strings.askAiHint) },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("aichat_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            val q = draftInput.trim()
                            if (q.isNotEmpty()) {
                                draftInput = ""
                                onSendMessage(q)
                            }
                        },
                        enabled = draftInput.isNotBlank() && !isSending && isAiConfigured,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("aichat_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send)
                    }
                }
            }
        }
    }

    if (showThreadPicker) {
        ModalBottomSheet(onDismissRequest = { showThreadPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(strings.selectSmsThreadTitle, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(strings.selectSmsThreadHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversations, key = { it.threadId }) { conv ->
                        val title = conv.contactName?.takeIf { it.isNotBlank() } ?: conv.address
                        val isSelected = conv.threadId == selectedThread?.threadId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectThread(conv.threadId)
                                    showThreadPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SenderAvatar(label = title, modifier = Modifier.size(38.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(conv.body, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomDateDialog) {
        BilingualDateRangeDialog(
            isPersian = isPersian,
            initialStartMs = contextConfig.customStartMs,
            initialEndMs = contextConfig.customEndMs,
            strings = strings,
            onDismiss = { showCustomDateDialog = false },
            onConfirm = { s, e ->
                showCustomDateDialog = false
                onSelectRangePreset(ArchiveRangePreset.CUSTOM, s, e)
            }
        )
    }

    if (xmlPreview != null) {
        XmlPreviewDialog(
            xmlResult = xmlPreview,
            isPersian = isPersian,
            strings = strings,
            onDismiss = onDismissXmlPreview
        )
    }

    if (showArchiveSheet) {
        var archiveSearch by remember { mutableStateOf("") }
        val filteredArchive = remember(archivedSessions, archiveSearch) {
            val q = archiveSearch.trim().lowercase()
            if (q.isEmpty()) archivedSessions
            else archivedSessions.filter {
                it.topic.title.lowercase().contains(q) ||
                    it.firstUserPrompt.lowercase().contains(q) ||
                    it.latestAnswerPreview.lowercase().contains(q) ||
                    it.parsedSummary.lowercase().contains(q)
            }
        }
        ModalBottomSheet(onDismissRequest = { showArchiveSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(strings.savedAiChatsLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = archiveSearch,
                    onValueChange = { archiveSearch = it },
                    placeholder = { Text(strings.archiveSearchHint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (archiveSearch.isNotEmpty()) {
                            IconButton(onClick = { archiveSearch = "" }) {
                                Icon(Icons.Default.Close, contentDescription = strings.closeSearch)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("aichat_archive_search_input")
                )
                Spacer(Modifier.height(12.dp))
                if (filteredArchive.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.noArchiveItemsTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredArchive, key = { it.topic.id }) { session ->
                            val isSelected = session.topic.id == activeTopicId
                            val originLabel = if (session.topic.originScreen == "AICHAT") {
                                strings.originAiChatTab
                            } else {
                                strings.originAiTopicsTab
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("aichat_archive_item_${session.topic.id}")
                                    .clickable {
                                        onLoadArchivedSession(session.topic.id)
                                        showArchiveSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = originLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = session.topic.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        val preview = session.parsedSummary.ifBlank { session.latestAnswerPreview }
                                        if (preview.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = preview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = buildString {
                                                append("${DateUtils.formatNumber(session.messageCount, isPersian)} ${strings.messagesCountLabel}")
                                                if (session.matchedRefsCount > 0) {
                                                    append(" • ${DateUtils.formatNumber(session.matchedRefsCount, isPersian)} ${strings.matchedItemsBadge}")
                                                }
                                                append(" • ${DateFormatter.formatChatTimestamp(session.topic.updatedAt, calendarMode, usePersianDigits = isPersian)}")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteArchivedSession(session.topic.id) },
                                        modifier = Modifier.testTag("aichat_archive_delete_${session.topic.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = strings.deleteTopic,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
}
