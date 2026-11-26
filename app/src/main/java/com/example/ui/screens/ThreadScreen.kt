package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalCalendarMode
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.local.DeliveryStatus
import com.example.data.local.SmsMessage
import com.example.ui.components.DeliveryStatusIndicator
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThreadScreen(
    threadId: Long,
    address: String,
    targetMessageId: Long,
    messages: List<SmsMessage>,
    hasSendSmsPermission: Boolean = true,
    onRequestSmsPermissions: () -> Unit = {},
    onBack: () -> Unit,
    onMarkRead: () -> Unit,
    onSendSms: (String) -> Unit,
    onDeleteMessage: (SmsMessage) -> Unit,
    onSaveContact: (String) -> Unit
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    val calendarMode = LocalCalendarMode.current
    var draft by remember { mutableStateOf("") }
    var messagePendingDelete by remember { mutableStateOf<SmsMessage?>(null) }
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
                        tonalElevation = if (isHighlighted) 6.dp else 1.dp,
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .testTag("sms_bubble_${msg.id}")
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { messagePendingDelete = msg }
                            )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = msg.body,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = DateUtils.formatDateTime(msg.timestamp, calendarMode, usePersianDigits = isPersian),
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
}
