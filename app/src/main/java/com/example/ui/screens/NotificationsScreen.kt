package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.local.NotificationItem
import com.example.ui.components.SenderAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notifications: List<NotificationItem>,
    isListenerEnabled: Boolean,
    isCaptureEnabledInSettings: Boolean,
    onOpenListenerSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDeleteNotification: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    var filterQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    val filtered = remember(notifications, filterQuery) {
        val q = filterQuery.trim().lowercase()
        if (q.isEmpty()) notifications
        else notifications.filter {
            it.appName.lowercase().contains(q) ||
                it.senderOrTitle.lowercase().contains(q) ||
                it.body.lowercase().contains(q) ||
                it.subText.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.notificationsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_notifications_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = strings.clearAllNotifications)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = !isListenerEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                strings.notifAccessDisabledTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                strings.notifAccessDisabledBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onOpenListenerSettings,
                            modifier = Modifier.testTag("enable_notif_access_button")
                        ) {
                            Text(strings.enableNotifAccess)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isListenerEnabled && !isCaptureEnabledInSettings) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.notifCapturePausedTitle, style = MaterialTheme.typography.titleSmall)
                            Text(
                                strings.notifCapturePausedBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = onOpenAppSettings) {
                            Text(strings.openSettings)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text(strings.filterAppsHint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(onClick = { filterQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("notifications_filter_input")
            )

            if (filtered.isEmpty()) {
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
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Text(strings.noNotificationsTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            strings.noNotificationsBody,
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
                    items(filtered, key = { it.id }) { notif ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SenderAvatar(
                                        label = notif.senderOrTitle,
                                        isNotification = true,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = notif.senderOrTitle,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = notif.appName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    IconButton(onClick = { onDeleteNotification(notif.id) }) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = strings.deleteMessage,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (notif.subText.isNotBlank()) "${notif.body}\n(${notif.subText})" else notif.body,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = DateUtils.formatDateTime(notif.timestamp, isPersian),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(strings.clearAllNotifications) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAll()
                    }
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
