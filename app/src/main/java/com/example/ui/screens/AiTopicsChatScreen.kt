package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.core.localization.AppStrings
import com.example.core.localization.DateFormatter
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalCalendarMode
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.ai.XmlArchiveBuildResult
import com.example.data.local.AiContextMode
import com.example.data.local.AiTopicEntity
import com.example.data.local.ArchiveRangePreset
import com.example.data.search.SearchResultItem
import com.example.ui.components.BilingualDateRangeDialog
import com.example.ui.components.MarkdownText
import com.example.ui.components.SearchResultCard
import com.example.ui.components.XmlPreviewDialog
import com.example.ui.viewmodel.AiChatDisplayMessage
import com.example.ui.viewmodel.AiScopeDraft

@Composable
fun AiTopicsChatPane(
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
    onMatchedItemClick: (SearchResultItem) -> Unit
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    val calendarMode = LocalCalendarMode.current
    val clipboard = LocalClipboardManager.current
    var promptText by remember { mutableStateOf("") }
    var copiedMsgId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size, isAiSending) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Isolated Topics Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onNewTopic,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("new_ai_topic_button")
            ) {
                Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.newTopic, style = MaterialTheme.typography.labelLarge)
            }

            for (topic in topics) {
                val selected = topic.id == activeTopicId
                InputChip(
                    selected = selected,
                    onClick = { onSelectTopic(topic.id) },
                    label = {
                        Text(
                            text = topic.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = strings.deleteTopic,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDeleteTopic(topic.id) }
                        )
                    },
                    modifier = Modifier.testTag("ai_topic_chip_${topic.id}")
                )
            }
        }

        // Active Scope Chip Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activePresetLabel = presetDisplayName(scopeDraft.preset, strings)
            val maxStr = DateUtils.formatNumber(scopeDraft.maxItems, isPersian)
            val modeShort = contextModeLabel(scopeDraft.contextMode, strings)
            AssistChip(
                onClick = onOpenScopeEditor,
                leadingIcon = {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        text = "$activePresetLabel • $modeShort ($maxStr)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("ai_scope_chip")
            )
            Text(
                text = if (activeTopic != null) {
                    DateFormatter.formatSmsTimestamp(activeTopic.updatedAt, calendarMode, usePersianDigits = isPersian)
                } else {
                    strings.newTopic
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isAiConfigured) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            strings.aiNotConfiguredTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            strings.aiNotConfiguredBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_from_ai_button")
                    ) {
                        Text(strings.openSettings)
                    }
                }
            }
        }

        // Chat Transcript Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatMessages.isEmpty() && !isAiSending) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Text(strings.noTopicsTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            strings.noTopicsBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chatMessages, key = { it.entity.id }) { item ->
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
                                            Text(
                                                text = "Error",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
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
                                                        onShowExistingXml(
                                                            msg.xmlContent,
                                                            msg.xmlSummaryLabel,
                                                            msg.xmlContent.toByteArray(Charsets.UTF_8).size
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                                                },
                                                label = {
                                                    Text(
                                                        text = msg.xmlSummaryLabel,
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
                                                .testTag("copy_ai_topic_msg_${msg.id}")
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
                                Text(
                                    text = strings.matchedReferencesTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                Spacer(Modifier.height(6.dp))
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
                                            strings = strings,
                                            onClick = { onMatchedItemClick(matched) }
                                        )
                                    }
                                }
                            }

                            val followUps = item.parsedResponse?.suggestedFollowUps.orEmpty()
                            if (!isUser && !msg.isError && followUps.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = strings.suggestedFollowUpsLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (q in followUps) {
                                        AssistChip(
                                            onClick = {
                                                if (!isAiSending && isAiConfigured) {
                                                    onAskQuestion(q)
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = q,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isAiSending) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = strings.aiThinking,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chat Input Bar
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = { Text(strings.askAiHint) },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_chat_input")
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val q = promptText.trim()
                        if (q.isNotEmpty()) {
                            promptText = ""
                            onAskQuestion(q)
                        }
                    },
                    enabled = promptText.isNotBlank() && !isAiSending && isAiConfigured,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("ai_chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send)
                }
            }
        }
    }

    if (showScopeSheet) {
        ArchiveHistoryPromptSheet(
            draft = scopeDraft,
            counts = draftCounts,
            hasPendingQuestion = !pendingQuestion.isNullOrBlank(),
            isPersian = isPersian,
            strings = strings,
            onDismiss = onDismissScopeSheet,
            onUpdateDraft = onUpdateScopeDraft,
            onPreviewXml = onPreviewXml,
            onConfirm = onConfirmScopeAndSend
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ArchiveHistoryPromptSheet(
    draft: AiScopeDraft,
    counts: Pair<Int, Int>,
    hasPendingQuestion: Boolean,
    isPersian: Boolean,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onUpdateDraft: ((AiScopeDraft) -> AiScopeDraft) -> Unit,
    onPreviewXml: () -> Unit,
    onConfirm: () -> Unit
) {
    var showCustomPicker by remember { mutableStateOf(false) }

    val presets = listOf(
        ArchiveRangePreset.TODAY,
        ArchiveRangePreset.TWO_DAYS,
        ArchiveRangePreset.THREE_DAYS,
        ArchiveRangePreset.ONE_WEEK,
        ArchiveRangePreset.TWO_WEEKS,
        ArchiveRangePreset.ONE_MONTH,
        ArchiveRangePreset.ONE_YEAR,
        ArchiveRangePreset.CUSTOM
    )
    val maxOptions = listOf(50, 150, 300, 500)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.selectHistoryPromptTitle,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = strings.selectHistoryPromptSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (preset in presets) {
                    val selected = draft.preset == preset
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (preset == ArchiveRangePreset.CUSTOM) {
                                showCustomPicker = true
                            } else {
                                onUpdateDraft { it.copy(preset = preset) }
                            }
                        },
                        label = { Text(presetDisplayName(preset, strings)) },
                        modifier = Modifier.testTag("ai_range_preset_${preset.name}")
                    )
                }
            }

            val calendarMode = LocalCalendarMode.current
            if (draft.preset == ArchiveRangePreset.CUSTOM && draft.customStartMs > 0L) {
                val startTxt = DateUtils.formatDateShort(draft.customStartMs, calendarMode, usePersianDigits = isPersian)
                val endTxt = DateUtils.formatDateShort(draft.customEndMs, calendarMode, usePersianDigits = isPersian)
                AssistChip(
                    onClick = { showCustomPicker = true },
                    leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("$startTxt – $endTxt") }
                )
            }

            HorizontalDivider()

            Text(strings.aiContextModeLabel, style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (draft.contextMode == AiContextMode.SMART_AUTO) strings.contextSmartAutoDesc else strings.contextNoUploadHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    AiContextMode.SMART_AUTO to strings.contextSmartAuto,
                    AiContextMode.COMPACT_TEXT to strings.contextCompactText,
                    AiContextMode.KEYWORD_PREFILTER to strings.contextKeywordPrefilter,
                    AiContextMode.INLINE_XML to strings.contextInlineXml
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = draft.contextMode == mode,
                        onClick = { onUpdateDraft { it.copy(contextMode = mode) } },
                        label = { Text(label) },
                        modifier = Modifier.testTag("ai_context_mode_${mode.name}")
                    )
                }
            }

            HorizontalDivider()

            Text(strings.maxItemsLabel, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (cap in maxOptions) {
                    FilterChip(
                        selected = draft.maxItems == cap,
                        onClick = { onUpdateDraft { it.copy(maxItems = cap) } },
                        label = { Text(DateUtils.formatNumber(cap, isPersian)) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.includeSms,
                    onClick = { onUpdateDraft { it.copy(includeSms = !it.includeSms) } },
                    label = { Text(strings.includeSmsLabel) }
                )
                FilterChip(
                    selected = draft.includeNotifications,
                    onClick = { onUpdateDraft { it.copy(includeNotifications = !it.includeNotifications) } },
                    label = { Text(strings.includeNotificationsLabel) }
                )
            }

            val smsN = DateUtils.formatNumber(counts.first, isPersian)
            val notifN = DateUtils.formatNumber(counts.second, isPersian)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${strings.matchedArchiveSummary}: $smsN ${strings.smsLabel} • $notifN ${strings.notificationLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPreviewXml,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.previewXml)
                }
                Button(
                    onClick = onConfirm,
                    enabled = draft.includeSms || draft.includeNotifications,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_ai_scope_button")
                ) {
                    Text(if (hasPendingQuestion) strings.sendToAiWithXml else strings.apply)
                }
            }
        }
    }

    if (showCustomPicker) {
        BilingualDateRangeDialog(
            isPersian = isPersian,
            initialStartMs = draft.customStartMs,
            initialEndMs = draft.customEndMs,
            strings = strings,
            onDismiss = { showCustomPicker = false },
            onConfirm = { s, e ->
                showCustomPicker = false
                onUpdateDraft {
                    it.copy(
                        preset = ArchiveRangePreset.CUSTOM,
                        customStartMs = s,
                        customEndMs = e
                    )
                }
            }
        )
    }
}

private fun contextModeLabel(mode: AiContextMode, strings: AppStrings): String = when (mode) {
    AiContextMode.SMART_AUTO -> strings.contextSmartAuto
    AiContextMode.COMPACT_TEXT -> strings.contextCompactText
    AiContextMode.KEYWORD_PREFILTER -> strings.contextKeywordPrefilter
    AiContextMode.INLINE_XML -> strings.contextInlineXml
}

private fun presetDisplayName(preset: ArchiveRangePreset, strings: AppStrings): String = when (preset) {
    ArchiveRangePreset.TODAY -> strings.presetToday
    ArchiveRangePreset.TWO_DAYS -> strings.presetTwoDays
    ArchiveRangePreset.THREE_DAYS -> strings.presetThreeDays
    ArchiveRangePreset.ONE_WEEK -> strings.presetOneWeek
    ArchiveRangePreset.TWO_WEEKS -> strings.presetTwoWeeks
    ArchiveRangePreset.ONE_MONTH -> strings.presetOneMonth
    ArchiveRangePreset.ONE_YEAR -> strings.presetOneYear
    ArchiveRangePreset.CUSTOM -> strings.presetCustomRange
}
