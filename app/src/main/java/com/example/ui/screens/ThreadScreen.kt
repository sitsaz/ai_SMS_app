package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.archive.ConversationExportFormat
import com.example.data.local.AiTopicWithMessages
import com.example.data.local.DeliveryStatus
import com.example.data.local.SmsMessage
import com.example.ui.components.DeliveryStatusIndicator
import com.example.ui.components.MarkdownText
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThreadScreen(
    threadId: Long,
    address: String,
    targetMessageId: Long,
    messages: List<SmsMessage>,
    pinnedMessages: List<SmsMessage> = emptyList(),
    threadAiHistory: List<AiTopicWithMessages> = emptyList(),
    hasSendSmsPermission: Boolean = true,
    onRequestSmsPermissions: () -> Unit = {},
    onBack: () -> Unit,
    onMarkRead: () -> Unit,
    onSendSms: (String) -> Unit,
    onTogglePinMessage: (SmsMessage, Boolean) -> Unit = { _, _ -> },
    onDeleteMessage: (SmsMessage) -> Unit,
    onSaveContact: (String) -> Unit,
    onAskAiAboutThread: () -> Unit = {},
    onOpenAiTopic: (Long) -> Unit = {},
    onExportThread: (ConversationExportFormat) -> Unit = {}
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    val calendarMode = LocalCalendarMode.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    var messagePendingDelete by remember { mutableStateOf<SmsMessage?>(null) }
    var showPinnedSheet by remember { mutableStateOf(false) }
    var showThreadAiHistorySheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var copiedMsgId by remember { mutableLongStateOf(-1L) }
    val listState = rememberLazyListState()

    val displayName = remember(messages, address) {
        messages.firstOrNull { !it.contactName.isNullOrBlank() }?.contactName ?: address
    }

    var highlightedId by remember(targetMessageId) { mutableLongStateOf(targetMessageId) }

    LaunchedEffect(threadId) {
        onMarkRead()
    }

    LaunchedEffect(messages.size, targetMessageId) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (targetMessageId > 0L) {
            val idx = messages.indexOfFirst { it.id == targetMessageId }
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
                highlightedId = targetMessageId
                delay(2400)
                highlightedId = -1L
            }
        } else {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    fun jumpToMessage(messageId: Long) {
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx >= 0) {
            scope.launch {
                listState.animateScrollToItem(idx)
                highlightedId = messageId
                delay(2200)
                highlightedId = -1L
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("thread_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (displayName != address) {
                            Text(
                                text = address,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (pinnedMessages.isNotEmpty()) {
                        IconButton(
                            onClick = { showPinnedSheet = true },
                            modifier = Modifier.testTag("thread_pinned_list_button")
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = strings.pinnedMessagesBanner,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (threadAiHistory.isNotEmpty()) {
                        IconButton(
                            onClick = { showThreadAiHistorySheet = true },
                            modifier = Modifier.testTag("thread_ai_history_button")
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = strings.threadAiHistoryBanner,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onAskAiAboutThread,
                        modifier = Modifier.testTag("thread_ask_ai_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = strings.askAiAboutThread)
                    }
                    if (messages.isNotEmpty()) {
                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                modifier = Modifier.testTag("thread_export_button")
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
                                        onExportThread(ConversationExportFormat.TEXT)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.exportAsJson) },
                                    onClick = {
                                        showExportMenu = false
                                        onExportThread(ConversationExportFormat.JSON)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { onSaveContact(address) },
                        modifier = Modifier.testTag("save_contact_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = strings.saveContact)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(strings.typeMessageHint) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sms_compose_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (!hasSendSmsPermission) {
                                onRequestSmsPermissions()
                            } else {
                                val text = draft.trim()
                                if (text.isNotEmpty()) {
                                    onSendSms(text)
                                    draft = ""
                                }
                            }
                        },
                        enabled = draft.isNotBlank() || !hasSendSmsPermission,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sms_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = pinnedMessages.isNotEmpty()) {
                val latestPinned = pinnedMessages.firstOrNull()
                if (latestPinned != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPinnedSheet = true }
                            .testTag("thread_pinned_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${strings.pinnedMessagesBanner} (${DateUtils.formatNumber(pinnedMessages.size, isPersian)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = latestPinned.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Text(
                                text = strings.viewAllPinned,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = threadAiHistory.isNotEmpty()) {
                val latestTopic = threadAiHistory.firstOrNull()
                if (latestTopic != null) {
                    val latestAnswer = latestTopic.messages
                        .lastOrNull { it.role == "ASSISTANT" && !it.isError }?.text
                        ?: latestTopic.messages.firstOrNull()?.text.orEmpty()
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThreadAiHistorySheet = true }
                            .testTag("thread_ai_history_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${strings.threadAiHistoryBanner} (${DateUtils.formatNumber(threadAiHistory.size, isPersian)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                if (latestAnswer.isNotBlank()) {
                                    Text(
                                        text = latestAnswer,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Text(
                                text = strings.viewAllPinned,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            if (!hasSendSmsPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = strings.sendSmsPermissionBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = onRequestSmsPermissions,
                            modifier = Modifier.testTag("thread_grant_send_sms_button")
                        ) {
                            Text(strings.grantPermissions)
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.emptyThreadHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isHighlighted = msg.id == highlightedId
                        val status = runCatching { DeliveryStatus.valueOf(msg.deliveryStatus) }
                            .getOrDefault(DeliveryStatus.NONE)
                        var showBubbleMenu by remember(msg.id) { mutableStateOf(false) }

                        val targetBg = when {
                            isHighlighted -> MaterialTheme.colorScheme.tertiaryContainer
                            msg.isOutgoing -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val bubbleColor by animateColorAsState(
                            targetValue = targetBg,
                            animationSpec = tween(durationMillis = 450),
                            label = "bubbleHighlight"
                        )
                        val contentColor = when {
                            isHighlighted -> MaterialTheme.colorScheme.onTertiaryContainer
                            msg.isOutgoing -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (msg.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (msg.isOutgoing) 18.dp else 4.dp,
                                    bottomEnd = if (msg.isOutgoing) 4.dp else 18.dp
                                ),
                                color = bubbleColor,
                                tonalElevation = if (isHighlighted || msg.isPinned) 5.dp else 1.dp,
                                modifier = Modifier
                                    .widthIn(max = 315.dp)
                                    .testTag("sms_bubble_${msg.id}")
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { showBubbleMenu = true }
                                    )
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    SelectionContainer {
                                        Text(
                                            text = msg.body,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = contentColor
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (msg.isPinned) {
                                            Icon(
                                                imageVector = Icons.Filled.PushPin,
                                                contentDescription = strings.pinnedThreadsBadge,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = DateFormatter.formatChatTimestamp(msg.timestamp, calendarMode, usePersianDigits = isPersian),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = contentColor.copy(alpha = 0.72f)
                                        )
                                        if (msg.isOutgoing) {
                                            DeliveryStatusIndicator(
                                                status = status,
                                                strings = strings,
                                                showLabel = true
                                            )
                                        }
                                        Spacer(Modifier.weight(1f, fill = false))
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(msg.body))
                                                copiedMsgId = msg.id
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("copy_sms_${msg.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (copiedMsgId == msg.id) Icons.Default.Check else Icons.Default.ContentCopy,
                                                contentDescription = strings.copyMessage,
                                                tint = contentColor.copy(alpha = 0.75f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onTogglePinMessage(msg, !msg.isPinned) },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("pin_sms_${msg.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (msg.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = if (msg.isPinned) strings.unpinMessage else strings.pinMessage,
                                                tint = if (msg.isPinned) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.65f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { showBubbleMenu = true },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = null,
                                                    tint = contentColor.copy(alpha = 0.65f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showBubbleMenu,
                                                onDismissRequest = { showBubbleMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(strings.copyMessage) },
                                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                                    onClick = {
                                                        clipboard.setText(AnnotatedString(msg.body))
                                                        copiedMsgId = msg.id
                                                        showBubbleMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(if (msg.isPinned) strings.unpinMessage else strings.pinMessage) },
                                                    leadingIcon = {
                                                        Icon(
                                                            if (msg.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        onTogglePinMessage(msg, !msg.isPinned)
                                                        showBubbleMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(strings.deleteMessage, color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                    },
                                                    onClick = {
                                                        showBubbleMenu = false
                                                        messagePendingDelete = msg
                                                    }
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
    }

    if (showPinnedSheet) {
        ModalBottomSheet(onDismissRequest = { showPinnedSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${strings.pinnedMessagesBanner} (${DateUtils.formatNumber(pinnedMessages.size, isPersian)})",
                    style = MaterialTheme.typography.titleLarge
                )
                if (pinnedMessages.isEmpty()) {
                    Text(
                        text = strings.noPinnedMessages,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pinnedMessages, key = { it.id }) { pinned ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    SelectionContainer {
                                        Text(
                                            text = pinned.body,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = DateFormatter.formatChatTimestamp(pinned.timestamp, calendarMode, usePersianDigits = isPersian),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row {
                                            TextButton(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(pinned.body))
                                                }
                                            ) {
                                                Text(strings.copyMessage)
                                            }
                                            TextButton(
                                                onClick = {
                                                    onTogglePinMessage(pinned, false)
                                                }
                                            ) {
                                                Text(strings.unpinMessage)
                                            }
                                            TextButton(
                                                onClick = {
                                                    showPinnedSheet = false
                                                    jumpToMessage(pinned.id)
                                                }
                                            ) {
                                                Text(strings.jumpToMessage)
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
    }

    messagePendingDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messagePendingDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(strings.deleteMessage) },
            text = { Text(msg.body.take(120)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMessage(msg)
                        messagePendingDelete = null
                    }
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { messagePendingDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showThreadAiHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showThreadAiHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${strings.threadAiHistoryBanner} (${DateUtils.formatNumber(threadAiHistory.size, isPersian)})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (threadAiHistory.isEmpty()) {
                    Text(
                        text = strings.noThreadAiHistory,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(threadAiHistory, key = { it.topic.id }) { session ->
                            val userMsg = session.messages.lastOrNull { it.role == "USER" }
                            val assistantMsg = session.messages.lastOrNull { it.role == "ASSISTANT" }
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("thread_ai_history_card_${session.topic.id}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = session.topic.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = DateFormatter.formatDualTimestamp(session.topic.updatedAt, calendarMode, usePersianDigits = isPersian),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (assistantMsg != null) {
                                        MarkdownText(
                                            markdown = assistantMsg.text,
                                            textColor = MaterialTheme.colorScheme.onSurface,
                                            baseStyle = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (userMsg != null && userMsg.xmlSummaryLabel.isNotBlank()) {
                                        Text(
                                            text = userMsg.xmlSummaryLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        if (assistantMsg != null) {
                                            TextButton(
                                                onClick = { clipboard.setText(AnnotatedString(assistantMsg.text)) }
                                            ) {
                                                Text(strings.copyMessage)
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                showThreadAiHistorySheet = false
                                                onOpenAiTopic(session.topic.id)
                                            }
                                        ) {
                                            Text(strings.continueAiChat)
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
}
