package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.localization.DateFormatter
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalCalendarMode
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import kotlinx.coroutines.launch
import com.example.data.local.ConversationRow
import com.example.data.local.DeliveryStatus
import com.example.data.search.SearchResultItem
import com.example.data.sms.ContactItem
import com.example.ui.components.ContactsPickerSheet
import com.example.ui.components.DeliveryStatusIndicator
import com.example.ui.components.SearchResultCard
import com.example.ui.components.SenderAvatar
import com.example.ui.viewmodel.SystemStatusState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessagesScreen(
    conversations: List<ConversationRow>,
    contacts: List<ContactItem>,
    systemStatus: SystemStatusState,
    showDefaultSmsBanner: Boolean,
    isSyncing: Boolean,
    isSearchExpanded: Boolean,
    searchQuery: String,
    searchResults: List<SearchResultItem>,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit = {},
    onRequestPermissions: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onDismissDefaultSmsBanner: () -> Unit,
    onSyncSms: () -> Unit,
    onOpenThread: (threadId: Long, address: String, targetMessageId: Long) -> Unit,
    onStartChatWithAddress: (address: String) -> Unit,
    onTogglePinThread: (threadId: Long, pinned: Boolean) -> Unit,
    onDeleteThread: (threadId: Long) -> Unit,
    onMarkAllRead: () -> Unit = {},
    onOpenArchive: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    val calendarMode = LocalCalendarMode.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showContactsSheet by remember { mutableStateOf(false) }
    var threadActionTarget by remember { mutableStateOf<ConversationRow?>(null) }
    var threadPendingDelete by remember { mutableStateOf<ConversationRow?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasUnread = remember(conversations) { conversations.any { it.unreadCount > 0 } }

    BackHandler(enabled = isSearchExpanded) {
        onSearchExpandedChange(false)
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = isSearchExpanded,
                label = "messagesTopBarSearch"
            ) { expanded ->
                if (expanded) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = {
                                    Text(
                                        text = strings.searchMessagesHint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        onSearchSubmit()
                                        keyboardController?.hide()
                                    }
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                                    .testTag("messages_top_search_input")
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { onSearchExpandedChange(false) },
                                modifier = Modifier.testTag("messages_top_search_collapse")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = strings.closeSearch
                                )
                            }
                        },
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.testTag("messages_top_search_clear")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = strings.closeSearch
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = strings.tabMessages,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { onSearchExpandedChange(true) },
                                modifier = Modifier.testTag("messages_top_search_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = strings.tabSearch
                                )
                            }
                            IconButton(
                                onClick = {
                                    onMarkAllRead()
                                    scope.launch { snackbarHostState.showSnackbar(strings.allMarkedAsRead) }
                                },
                                enabled = hasUnread || conversations.isNotEmpty(),
                                modifier = Modifier.testTag("mark_all_read_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = strings.markAllAsRead,
                                    tint = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onOpenArchive,
                                modifier = Modifier.testTag("messages_open_archive_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = strings.openArchiveButton
                                )
                            }
                            IconButton(
                                onClick = onSyncSms,
                                enabled = !isSyncing,
                                modifier = Modifier.testTag("sync_sms_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = strings.syncSmsNow)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (!isSearchExpanded) {
                ExtendedFloatingActionButton(
                    onClick = { showContactsSheet = true },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text(strings.newMessage) },
                    modifier = Modifier.testTag("new_message_fab")
                )
            }
        }
    ) { innerPadding ->
        if (isSearchExpanded && searchQuery.isNotBlank()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "${DateUtils.formatNumber(searchResults.size, isPersian)} ${strings.resultsCountLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                if (searchResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
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
                    }
                } else {
                    items(searchResults, key = { it.refId }) { item ->
                        SearchResultCard(
                            item = item,
                            highlightQuery = searchQuery,
                            isPersian = isPersian,
                            calendarMode = calendarMode,
                            strings = strings,
                            onClick = {
                                onSearchSubmit()
                                val tid = item.threadId ?: return@SearchResultCard
                                onOpenThread(tid, item.senderSecondary, item.itemId)
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    AnimatedVisibility(visible = !systemStatus.hasSmsPermissions) {
                        StatusActionCard(
                            icon = Icons.Default.Security,
                            title = strings.smsPermissionsMissingTitle,
                            body = strings.smsPermissionsMissingBody,
                            buttonLabel = strings.grantPermissions,
                            onClick = onRequestPermissions,
                            testTag = "grant_sms_permissions_button",
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                item {
                    AnimatedVisibility(visible = showDefaultSmsBanner) {
                        StatusActionCard(
                            icon = Icons.Default.Sms,
                            title = strings.defaultSmsBannerTitle,
                            body = strings.defaultSmsBannerBody,
                            buttonLabel = strings.setAsDefaultSms,
                            onClick = onRequestDefaultSms,
                            onDismiss = onDismissDefaultSmsBanner,
                            dismissContentDescription = strings.dismissForToday,
                            testTag = "set_default_sms_button",
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                if (conversations.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 56.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MarkChatUnread,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = strings.noConversationsTitle,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = strings.noConversationsBody,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(conversations, key = { it.threadId }) { conv ->
                        val displayName = conv.contactName?.takeIf { it.isNotBlank() } ?: conv.address
                        val status = runCatching { DeliveryStatus.valueOf(conv.deliveryStatus) }
                            .getOrDefault(DeliveryStatus.NONE)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("conversation_card_${conv.threadId}")
                                .combinedClickable(
                                    onClick = { onOpenThread(conv.threadId, conv.address, -1L) },
                                    onLongClick = { threadActionTarget = conv }
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    conv.isPinned -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    conv.unreadCount > 0 -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SenderAvatar(label = displayName)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (conv.isPinned) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = strings.pinnedThreadsBadge,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = if (conv.unreadCount > 0 || conv.isPinned) FontWeight.Bold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = DateFormatter.formatSmsTimestamp(
                                                timestampMs = conv.timestamp,
                                                mode = calendarMode,
                                                usePersianDigits = isPersian
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (conv.unreadCount > 0) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (conv.isOutgoing) {
                                                DeliveryStatusIndicator(
                                                    status = status,
                                                    strings = strings,
                                                    showLabel = false
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = conv.body,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (conv.unreadCount > 0) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                fontWeight = if (conv.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (conv.unreadCount > 0) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        text = DateUtils.formatNumber(conv.unreadCount, isPersian),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onTogglePinThread(conv.threadId, !conv.isPinned) },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("pin_thread_button_${conv.threadId}")
                                            ) {
                                                Icon(
                                                    imageVector = if (conv.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                    contentDescription = if (conv.isPinned) strings.unpinThread else strings.pinThread,
                                                    tint = if (conv.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showContactsSheet) {
        ContactsPickerSheet(
            contacts = contacts,
            strings = strings,
            onSelectAddress = { addr ->
                showContactsSheet = false
                onStartChatWithAddress(addr)
            },
            onDismiss = { showContactsSheet = false }
        )
    }

    threadActionTarget?.let { conv ->
        val displayTitle = conv.contactName?.takeIf { it.isNotBlank() } ?: conv.address
        AlertDialog(
            onDismissRequest = { threadActionTarget = null },
            title = { Text(displayTitle) },
            text = { Text(conv.body.take(120)) },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        onTogglePinThread(conv.threadId, !conv.isPinned)
                        threadActionTarget = null
                    }
                ) {
                    Icon(
                        imageVector = if (conv.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (conv.isPinned) strings.unpinThread else strings.pinThread)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            threadActionTarget = null
                            threadPendingDelete = conv
                        }
                    ) {
                        Text(strings.deleteThread, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { threadActionTarget = null }) {
                        Text(strings.cancel)
                    }
                }
            }
        )
    }

    threadPendingDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { threadPendingDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(strings.deleteThread) },
            text = { Text(conv.contactName ?: conv.address) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteThread(conv.threadId)
                        threadPendingDelete = null
                    }
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { threadPendingDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun StatusActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    buttonLabel: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    dismissContentDescription: String = ""
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.testTag(testTag)
            ) {
                Text(buttonLabel)
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_default_sms_banner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = dismissContentDescription,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
