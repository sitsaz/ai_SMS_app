package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppStrings
import com.example.core.localization.CalendarMode
import com.example.core.localization.DateFormatter
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalCalendarMode
import com.example.data.ai.XmlArchiveBuildResult
import com.example.data.local.DeliveryStatus
import com.example.data.search.ArchiveItemSource
import com.example.data.search.SearchResultItem
import com.example.data.sms.ContactItem
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun SenderAvatar(
    label: String,
    isNotification: Boolean = false,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val brush = remember(isNotification, primary, secondary) {
        if (isNotification) {
            Brush.linearGradient(listOf(secondary.copy(alpha = 0.85f), primary.copy(alpha = 0.7f)))
        } else {
            Brush.linearGradient(listOf(primary.copy(alpha = 0.9f), secondary.copy(alpha = 0.75f)))
        }
    }
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        if (isNotification) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        } else if (initial.all { it.isDigit() || it == '+' }) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DeliveryStatusIndicator(
    status: DeliveryStatus,
    strings: AppStrings,
    showLabel: Boolean = true
) {
    val (icon, tint, label) = when (status) {
        DeliveryStatus.SENDING -> Triple(
            Icons.Default.Schedule,
            MaterialTheme.colorScheme.onSurfaceVariant,
            strings.statusSending
        )
        DeliveryStatus.SENT -> Triple(
            Icons.Default.Check,
            MaterialTheme.colorScheme.onSurfaceVariant,
            strings.statusSent
        )
        DeliveryStatus.DELIVERED -> Triple(
            Icons.Default.DoneAll,
            MaterialTheme.colorScheme.secondary,
            strings.statusDelivered
        )
        DeliveryStatus.FAILED -> Triple(
            Icons.Default.ErrorOutline,
            MaterialTheme.colorScheme.error,
            strings.statusFailed
        )
        DeliveryStatus.NONE -> return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

@Composable
fun SearchResultCard(
    item: SearchResultItem,
    highlightQuery: String,
    isPersian: Boolean,
    strings: AppStrings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    calendarMode: CalendarMode = LocalCalendarMode.current
) {
    val isNotif = item.source == ArchiveItemSource.NOTIFICATION
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
    val clipboard = LocalClipboardManager.current
    var copied by remember(item.refId) { mutableStateOf(false) }

    val annotatedBody = remember(item.body, highlightQuery, highlightBg, highlightFg) {
        buildHighlightedText(item.body, highlightQuery, highlightBg, highlightFg)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_result_card_${item.refId}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SenderAvatar(
                    label = item.senderDisplayName,
                    isNotification = isNotif,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.senderDisplayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.senderSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (isNotif) MaterialTheme.typography.bodySmall.fontFamily else JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isNotif) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isNotif) Icons.Default.Notifications else Icons.Default.Sms,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isNotif) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Text(
                            text = if (isNotif) strings.notificationLabel else strings.smsLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isNotif) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Complete, untruncated message body with partial selection (and Markdown when unhighlighted)
            if (highlightQuery.isBlank()) {
                MarkdownText(
                    markdown = item.body,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    baseStyle = MaterialTheme.typography.bodyMedium
                )
            } else {
                SelectionContainer {
                    Text(
                        text = annotatedBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateFormatter.formatChatTimestamp(item.timestamp, calendarMode, usePersianDigits = isPersian),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(item.body))
                            copied = true
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("copy_search_result_${item.refId}")
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = strings.copyMessage,
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (!isNotif) {
                        if (item.isOutgoing) {
                            DeliveryStatusIndicator(
                                status = item.deliveryStatus,
                                strings = strings,
                                showLabel = true
                            )
                        } else {
                            Text(
                                text = strings.incoming,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (item.threadId != null) {
                        Text(
                            text = strings.tapToOpenInThread,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun buildHighlightedText(
    body: String,
    query: String,
    highlightBg: androidx.compose.ui.graphics.Color,
    highlightFg: androidx.compose.ui.graphics.Color
): AnnotatedString {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty()) return AnnotatedString(body)
    return buildAnnotatedString {
        append(body)
        val lowerBody = body.lowercase()
        val lowerQuery = cleanQuery.lowercase()
        var index = lowerBody.indexOf(lowerQuery)
        while (index >= 0) {
            addStyle(
                style = SpanStyle(
                    background = highlightBg,
                    color = highlightFg,
                    fontWeight = FontWeight.Bold
                ),
                start = index,
                end = index + lowerQuery.length
            )
            index = lowerBody.indexOf(lowerQuery, index + lowerQuery.length)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsPickerSheet(
    contacts: List<ContactItem>,
    strings: AppStrings,
    onSelectAddress: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(contacts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) contacts
        else contacts.filter {
            it.displayName.lowercase().contains(q) || it.phoneNumber.lowercase().contains(q)
        }
    }
    val looksLikePhone = remember(query) {
        val trimmed = query.trim()
        trimmed.length >= 3 && trimmed.any { it.isDigit() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = strings.selectContactTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.searchContactsOrNumberHint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contacts_search_input")
            )

            if (looksLikePhone) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = { onSelectAddress(query.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_chat_raw_number_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${strings.startChatWithNumber} ${query.trim()}")
                }
            }

            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noContactsFound,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { "${it.contactId}_${it.phoneNumber}" }) { contact ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAddress(contact.phoneNumber) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SenderAvatar(label = contact.displayName, modifier = Modifier.size(38.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (contact.label.isNotBlank()) {
                                    AssistChip(onClick = { onSelectAddress(contact.phoneNumber) }, label = { Text(contact.label) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun XmlPreviewDialog(
    xmlResult: XmlArchiveBuildResult,
    isPersian: Boolean,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.xmlPreviewTitle, style = MaterialTheme.typography.titleLarge)
                val sizeKb = String.format(java.util.Locale.US, "%.1f KB", xmlResult.totalBytes / 1024.0)
                Text(
                    text = "${xmlResult.rangeLabel} • $sizeKb",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 380.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = xmlResult.xmlContent,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(xmlResult.xmlContent))
                    copied = true
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (copied) strings.copiedToClipboard else strings.copyXml)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun BilingualDateRangeDialog(
    isPersian: Boolean,
    initialStartMs: Long,
    initialEndMs: Long,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
    calendarMode: CalendarMode = LocalCalendarMode.current
) {
    val now = System.currentTimeMillis()
    val defaultStart = if (initialStartMs > 0L) initialStartMs else now - 6L * 86_400_000L
    val defaultEnd = if (initialEndMs > 0L) initialEndMs else now

    val startInit = remember(defaultStart, calendarMode) {
        DateUtils.getYearMonthDay(defaultStart, calendarMode)
    }
    val endInit = remember(defaultEnd, calendarMode) {
        DateUtils.getYearMonthDay(defaultEnd, calendarMode)
    }

    var sYear by remember(startInit) { mutableIntStateOf(startInit.first) }
    var sMonth by remember(startInit) { mutableIntStateOf(startInit.second) }
    var sDay by remember(startInit) { mutableIntStateOf(startInit.third) }

    var eYear by remember(endInit) { mutableIntStateOf(endInit.first) }
    var eMonth by remember(endInit) { mutableIntStateOf(endInit.second) }
    var eDay by remember(endInit) { mutableIntStateOf(endInit.third) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.filterDateRange, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateStepperSection(
                    title = strings.customStartDate,
                    year = sYear,
                    month = sMonth,
                    day = sDay,
                    isPersian = isPersian,
                    calendarMode = calendarMode,
                    strings = strings,
                    onYearChange = { y ->
                        sYear = y
                        val maxD = DateUtils.daysInMonth(y, sMonth, calendarMode)
                        if (sDay > maxD) sDay = maxD
                    },
                    onMonthChange = { m ->
                        sMonth = m
                        val maxD = DateUtils.daysInMonth(sYear, m, calendarMode)
                        if (sDay > maxD) sDay = maxD
                    },
                    onDayChange = { sDay = it }
                )
                HorizontalDivider()
                DateStepperSection(
                    title = strings.customEndDate,
                    year = eYear,
                    month = eMonth,
                    day = eDay,
                    isPersian = isPersian,
                    calendarMode = calendarMode,
                    strings = strings,
                    onYearChange = { y ->
                        eYear = y
                        val maxD = DateUtils.daysInMonth(y, eMonth, calendarMode)
                        if (eDay > maxD) eDay = maxD
                    },
                    onMonthChange = { m ->
                        eMonth = m
                        val maxD = DateUtils.daysInMonth(eYear, m, calendarMode)
                        if (eDay > maxD) eDay = maxD
                    },
                    onDayChange = { eDay = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startMs = DateUtils.toEpochMillis(sYear, sMonth, sDay, calendarMode, endOfDay = false)
                    val endMs = DateUtils.toEpochMillis(eYear, eMonth, eDay, calendarMode, endOfDay = true)
                    val orderedStart = minOf(startMs, endMs)
                    val orderedEnd = maxOf(startMs, endMs)
                    onConfirm(orderedStart, orderedEnd)
                }
            ) {
                Text(strings.apply)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
private fun DateStepperSection(
    title: String,
    year: Int,
    month: Int,
    day: Int,
    isPersian: Boolean,
    calendarMode: CalendarMode,
    strings: AppStrings,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit
) {
    val maxDay = DateUtils.daysInMonth(year, month, calendarMode)
    val monthLabel = DateUtils.monthName(month, calendarMode)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniStepper(
                label = strings.yearLabel,
                display = DateUtils.formatNumber(year, isPersian),
                onDec = { onYearChange(year - 1) },
                onInc = { onYearChange(year + 1) }
            )
            MiniStepper(
                label = strings.monthLabel,
                display = monthLabel,
                onDec = { onMonthChange(if (month <= 1) 12 else month - 1) },
                onInc = { onMonthChange(if (month >= 12) 1 else month + 1) }
            )
            MiniStepper(
                label = strings.dayLabel,
                display = DateUtils.formatNumber(day.coerceIn(1, maxDay), isPersian),
                onDec = { onDayChange(if (day <= 1) maxDay else day - 1) },
                onInc = { onDayChange(if (day >= maxDay) 1 else day + 1) }
            )
        }
    }
}

@Composable
private fun MiniStepper(
    label: String,
    display: String,
    onDec: () -> Unit,
    onInc: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDec, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = display,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onInc, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun SmsPermissionRationaleDialog(
    strings: AppStrings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = strings.smsRationaleTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.smsRationaleBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(strings.smsRationaleReadPoint, style = MaterialTheme.typography.bodySmall)
                        Text(strings.smsRationaleSendPoint, style = MaterialTheme.typography.bodySmall)
                        Text(strings.smsRationaleContactsPoint, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("sms_rationale_continue_button")
            ) {
                Text(strings.continueAction)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("sms_rationale_cancel_button")
            ) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun SmsPermissionSettingsDialog(
    strings: AppStrings,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = strings.smsPermissionDeniedTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = strings.smsPermissionDeniedBody,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("sms_permission_open_settings_button")
            ) {
                Text(strings.openAppPermissions)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
