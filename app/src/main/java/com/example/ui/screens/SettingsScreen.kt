package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.localization.AppLanguage
import com.example.core.localization.CalendarPreference
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalCalendarMode
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.ai.AiSearchClient
import com.example.data.local.AiProviderType
import com.example.data.local.BackupSyncMode
import com.example.data.local.ThemeMode
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsRepository
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.ConnectionTestState
import com.example.ui.viewmodel.DriveBackupUiState
import com.example.ui.viewmodel.ModelsFetchState
import com.example.ui.viewmodel.SystemStatusState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    systemStatus: SystemStatusState,
    testState: ConnectionTestState,
    modelsState: ModelsFetchState,
    driveState: DriveBackupUiState,
    apkPackageName: String,
    apkSha1Fingerprint: String,
    onLanguageChange: (AppLanguage) -> Unit,
    onCalendarPreferenceChange: (CalendarPreference) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onCaptureNotificationsChange: (Boolean) -> Unit,
    onSmsNotificationsChange: (Boolean) -> Unit,
    onNotificationPreviewChange: (Boolean) -> Unit,
    onNotificationVibrateChange: (Boolean) -> Unit,
    onPickSmsRingtone: () -> Unit,
    onOpenSmsChannelSettings: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onSaveAiConfig: (AiProviderType, String, String, String, Int) -> Unit,
    onTestAiConnection: (AiProviderType, String, String, String, Int) -> Unit,
    onFetchModels: (AiProviderType, String, String) -> Unit,
    onClearAiConfig: () -> Unit,
    onBackupScopeChange: (Boolean, Boolean) -> Unit,
    onBackupSyncModeChange: (BackupSyncMode) -> Unit,
    onConnectGoogleDrive: () -> Unit,
    onConnectDriveFallbackAccount: () -> Unit,
    onLaunchDriveBrowserPkce: (String) -> Unit,
    onCompleteDriveBrowserPkce: (String) -> Unit,
    onConnectDriveWithToken: (String) -> Unit,
    onToggleDriveTroubleshooting: (Boolean) -> Unit,
    onDisconnectGoogleDrive: () -> Unit,
    onRefreshDriveBackups: () -> Unit,
    onBackupToGoogleDrive: () -> Unit,
    onRestoreFromGoogleDrive: (String) -> Unit,
    onDeleteDriveBackup: (String) -> Unit,
    onExportLocalBackup: () -> Unit,
    onImportLocalBackup: () -> Unit
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current
    val calendarMode = LocalCalendarMode.current
    val clipboard = LocalClipboardManager.current

    var provider by remember(settings.aiProviderType) { mutableStateOf(settings.aiProviderType) }
    var baseUrl by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var modelName by remember(settings.aiModelName) { mutableStateOf(settings.aiModelName) }
    var apiKey by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
    var contextLimitText by remember(settings.aiContextLimitTokens) {
        mutableStateOf(settings.aiContextLimitTokens.toString())
    }
    var showKey by remember { mutableStateOf(false) }
    var inlineStatusMessage by remember { mutableStateOf<String?>(null) }

    var customClientId by remember(settings.driveCustomClientId) {
        mutableStateOf(settings.driveCustomClientId)
    }
    var manualPkceCodeInput by remember { mutableStateOf("") }
    var manualAccessTokenInput by remember { mutableStateOf("") }
    var diagCopiedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(
        settings.aiProviderType,
        settings.aiBaseUrl,
        settings.aiModelName,
        settings.aiApiKey,
        settings.aiContextLimitTokens
    ) {
        provider = settings.aiProviderType
        baseUrl = settings.aiBaseUrl
        modelName = settings.aiModelName
        apiKey = settings.aiApiKey
        contextLimitText = settings.aiContextLimitTokens.toString()
    }

    LaunchedEffect(settings.driveCustomClientId) {
        if (customClientId.isBlank() && settings.driveCustomClientId.isNotBlank()) {
            customClientId = settings.driveCustomClientId
        }
    }

    val resolvedContextLimit = contextLimitText.toIntOrNull()?.coerceIn(1_024, 2_000_000)
        ?: settings.aiContextLimitTokens

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.settingsTitle,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Language & Calendar
            SettingsSectionCard(
                icon = Icons.Default.Language,
                title = strings.sectionLanguage
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = settings.language == AppLanguage.SYSTEM,
                        onClick = { onLanguageChange(AppLanguage.SYSTEM) },
                        label = { Text(strings.languageSystem) },
                        modifier = Modifier.testTag("lang_system_chip")
                    )
                    FilterChip(
                        selected = settings.language == AppLanguage.ENGLISH,
                        onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                        label = { Text(strings.languageEnglish) },
                        modifier = Modifier.testTag("lang_english_chip")
                    )
                    FilterChip(
                        selected = settings.language == AppLanguage.PERSIAN,
                        onClick = { onLanguageChange(AppLanguage.PERSIAN) },
                        label = { Text(strings.languagePersian) },
                        modifier = Modifier.testTag("lang_persian_chip")
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Spacer(Modifier.height(10.dp))

                Text(strings.calendarModeLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = settings.calendarPreference == CalendarPreference.AUTO,
                        onClick = { onCalendarPreferenceChange(CalendarPreference.AUTO) },
                        label = { Text(strings.calendarAuto) },
                        modifier = Modifier.testTag("calendar_auto_chip")
                    )
                    FilterChip(
                        selected = settings.calendarPreference == CalendarPreference.JALALI,
                        onClick = { onCalendarPreferenceChange(CalendarPreference.JALALI) },
                        label = { Text(strings.calendarJalali) },
                        modifier = Modifier.testTag("calendar_jalali_chip")
                    )
                    FilterChip(
                        selected = settings.calendarPreference == CalendarPreference.GREGORIAN,
                        onClick = { onCalendarPreferenceChange(CalendarPreference.GREGORIAN) },
                        label = { Text(strings.calendarGregorian) },
                        modifier = Modifier.testTag("calendar_gregorian_chip")
                    )
                }
            }

            // 2. Default SMS & Notification Archive
            SettingsSectionCard(
                icon = Icons.Default.Sms,
                title = strings.sectionMessagingAndArchive
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (systemStatus.isDefaultSmsApp) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    onClick = {
                        if (!systemStatus.isDefaultSmsApp) onRequestDefaultSms()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_default_sms_row")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (systemStatus.isDefaultSmsApp) {
                                strings.defaultSmsStatusActive
                            } else {
                                strings.defaultSmsStatusInactive
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (systemStatus.isDefaultSmsApp) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.captureNotificationsToggle, style = MaterialTheme.typography.titleSmall)
                        Text(
                            strings.captureNotificationsSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.captureNotifications,
                        onCheckedChange = onCaptureNotificationsChange,
                        modifier = Modifier.testTag("capture_notifications_switch")
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onOpenNotificationListenerSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_notif_access_button")
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${strings.systemNotificationAccess}: " +
                            if (systemStatus.isNotificationListenerEnabled) "ON" else "OFF"
                    )
                }
            }

            // 3. Notification Display & Ringtone Settings
            SettingsSectionCard(
                icon = Icons.Default.NotificationsActive,
                title = strings.sectionNotificationSettings
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.incomingSmsNotifToggle, style = MaterialTheme.typography.titleSmall)
                        Text(
                            strings.incomingSmsNotifSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.smsNotificationsEnabled,
                        onCheckedChange = onSmsNotificationsChange,
                        modifier = Modifier.testTag("sms_notifications_switch")
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.notifPreviewTextToggle, style = MaterialTheme.typography.titleSmall)
                        Text(
                            strings.notifPreviewTextSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.showNotificationPreviewText,
                        onCheckedChange = onNotificationPreviewChange,
                        enabled = settings.smsNotificationsEnabled,
                        modifier = Modifier.testTag("notif_preview_switch")
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.notifVibrateToggle,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.notificationVibrate,
                        onCheckedChange = onNotificationVibrateChange,
                        enabled = settings.smsNotificationsEnabled,
                        modifier = Modifier.testTag("notif_vibrate_switch")
                    )
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = onPickSmsRingtone,
                    enabled = settings.smsNotificationsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_sms_ringtone_row")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.smsRingtoneLabel,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = settings.smsRingtoneTitle.ifBlank { strings.ringtoneDefault },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onOpenSmsChannelSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_sms_channel_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsApplications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.openSystemChannelSettings)
                }
            }

            // 4. AI API Configuration + Context Window & Capabilities
            SettingsSectionCard(
                icon = Icons.Default.Key,
                title = strings.sectionAiConfiguration
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.aiSetupHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val clip = clipboard.getText()?.text.orEmpty()
                        val (parsedUrl, parsedKey) = AiSearchClient.parseCredentialsText(clip)
                        if (parsedUrl == null && parsedKey == null) {
                            inlineStatusMessage = strings.smartPasteEmpty
                        } else {
                            if (parsedUrl != null) {
                                baseUrl = parsedUrl
                                if (parsedUrl.contains("generativelanguage.googleapis.com", ignoreCase = true)) {
                                    provider = AiProviderType.GEMINI_REST
                                } else if (provider == AiProviderType.GEMINI_REST) {
                                    provider = AiProviderType.OPENAI_COMPATIBLE
                                }
                            }
                            if (parsedKey != null) {
                                apiKey = parsedKey
                            }
                            inlineStatusMessage = strings.smartPasteApplied
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_paste_ai_config_button")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.smartPasteConfig)
                }

                Spacer(Modifier.height(10.dp))

                Text(strings.aiProviderLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        AiProviderType.OPENAI_COMPATIBLE to strings.providerOpenAiCompat,
                        AiProviderType.GEMINI_REST to strings.providerGeminiRest,
                        AiProviderType.CUSTOM to strings.providerCustom
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = provider == type,
                            onClick = {
                                val prevDefaultUrl = SettingsRepository.defaultBaseUrlFor(provider)
                                val prevDefaultModel = SettingsRepository.defaultModelFor(provider)
                                provider = type
                                if (baseUrl.isBlank() || baseUrl == prevDefaultUrl) {
                                    baseUrl = SettingsRepository.defaultBaseUrlFor(type)
                                }
                                if (modelName.isBlank() || modelName == prevDefaultModel) {
                                    modelName = SettingsRepository.defaultModelFor(type)
                                }
                                inlineStatusMessage = null
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        inlineStatusMessage = null
                    },
                    label = { Text(strings.aiBaseUrlLabel) },
                    placeholder = { Text("https://.../v1") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_base_url_input")
                )

                if (AiSearchClient.isValidHttpUrl(baseUrl)) {
                    val resolvedChatPreview = remember(provider, baseUrl, modelName) {
                        when (provider) {
                            AiProviderType.GEMINI_REST -> {
                                val b = AiSearchClient.normalizeBaseUrl(baseUrl)
                                val m = modelName.trim().ifBlank { "gemini-2.5-flash" }.removePrefix("models/")
                                if (b.contains(":generateContent")) b else "$b/models/$m:generateContent"
                            }
                            AiProviderType.OPENAI_COMPATIBLE,
                            AiProviderType.CUSTOM -> AiSearchClient.buildOpenAiChatUrl(baseUrl)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${strings.resolvedEndpointLabel}: $resolvedChatPreview",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        inlineStatusMessage = null
                    },
                    label = { Text(strings.aiApiKeyLabel) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_api_key_input")
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = {
                            modelName = it
                            inlineStatusMessage = null
                        },
                        label = { Text(strings.aiModelLabel) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_model_input")
                    )
                    OutlinedButton(
                        onClick = { onFetchModels(provider, baseUrl, apiKey) },
                        enabled = modelsState !is ModelsFetchState.Loading &&
                            AiSearchClient.isValidHttpUrl(baseUrl) &&
                            apiKey.isNotBlank(),
                        modifier = Modifier.testTag("fetch_models_button")
                    ) {
                        if (modelsState is ModelsFetchState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.fetchModels)
                        }
                    }
                }

                when (modelsState) {
                    is ModelsFetchState.Success -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${strings.fetchedModelsLabel} (${DateUtils.formatNumber(modelsState.models.size, isPersian)})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            modelsState.models.take(20).forEach { fetchedModel ->
                                FilterChip(
                                    selected = modelName.trim() == fetchedModel,
                                    onClick = {
                                        modelName = fetchedModel
                                        inlineStatusMessage = null
                                    },
                                    label = {
                                        Text(
                                            text = fetchedModel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                        )
                                    },
                                    modifier = Modifier.testTag("fetched_model_chip")
                                )
                            }
                        }
                    }
                    is ModelsFetchState.Error -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = modelsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Unit
                }

                Spacer(Modifier.height(10.dp))

                // Context window limit setting + preset chips
                OutlinedTextField(
                    value = contextLimitText,
                    onValueChange = { raw ->
                        contextLimitText = raw.filter { it.isDigit() }.take(7)
                        inlineStatusMessage = null
                    },
                    label = { Text(strings.aiContextLimitLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_context_limit_input")
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(4_096, 8_192, 16_384, 32_000, 65_536, 128_000).forEach { presetTokens ->
                        FilterChip(
                            selected = resolvedContextLimit == presetTokens,
                            onClick = { contextLimitText = presetTokens.toString() },
                            label = {
                                Text(
                                    text = "${presetTokens / 1000}K",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveAiConfig(provider, baseUrl, modelName, apiKey, resolvedContextLimit)
                            inlineStatusMessage = strings.aiConfigSaved
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_ai_config_button")
                    ) {
                        Text(strings.saveAiConfig)
                    }
                    OutlinedButton(
                        onClick = {
                            inlineStatusMessage = null
                            onTestAiConnection(provider, baseUrl, modelName, apiKey, resolvedContextLimit)
                        },
                        enabled = testState !is ConnectionTestState.Testing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_ai_connection_button")
                    ) {
                        if (testState is ConnectionTestState.Testing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(strings.testAiConnection)
                        }
                    }
                }

                if (apiKey.isNotBlank() || settings.aiApiKey.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                onClearAiConfig()
                                inlineStatusMessage = strings.aiConfigCleared
                            },
                            modifier = Modifier.testTag("clear_ai_config_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.clearAiConfig)
                        }
                    }
                }

                inlineStatusMessage?.let { msg ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Detected or persisted AI capabilities (file upload, image input, context length)
                val showCapabilities = testState is ConnectionTestState.Success || settings.aiCapabilitiesChecked
                val capFileSupported = (testState as? ConnectionTestState.Success)?.supportsFileUpload
                    ?: settings.aiSupportsFileUpload
                val capImageSupported = (testState as? ConnectionTestState.Success)?.supportsImageInput
                    ?: settings.aiSupportsImageInput
                val detectedCtx = (testState as? ConnectionTestState.Success)?.detectedContextLength

                if (showCapabilities) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = strings.aiCapabilitiesLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CapabilityBadge(
                            icon = Icons.Default.AttachFile,
                            label = if (capFileSupported) {
                                strings.capabilityFileSupported
                            } else {
                                strings.capabilityFileUnsupported
                            },
                            supported = capFileSupported
                        )
                        CapabilityBadge(
                            icon = Icons.Default.Image,
                            label = if (capImageSupported) {
                                strings.capabilityImageSupported
                            } else {
                                strings.capabilityImageUnsupported
                            },
                            supported = capImageSupported
                        )
                        if (detectedCtx != null) {
                            FilterChip(
                                selected = resolvedContextLimit == detectedCtx,
                                onClick = { contextLimitText = detectedCtx.toString() },
                                label = {
                                    Text(
                                        text = "${strings.capabilityDetectedCtx}: ${DateUtils.formatNumber(detectedCtx, isPersian)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }

                when (testState) {
                    is ConnectionTestState.Success -> {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "OK: ${testState.preview}" +
                                            if (testState.capabilitySummary.isNotBlank()) "\n${testState.capabilitySummary}" else "",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    is ConnectionTestState.Error -> {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = testState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }

            // 5. Google Drive Backup & Restore + Device OAuth Fallbacks
            SettingsSectionCard(
                icon = Icons.Default.Backup,
                title = strings.sectionGoogleDriveBackup
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (driveState.isConnected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (driveState.isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (driveState.isConnected) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (driveState.isConnected) {
                                    val email = driveState.accountEmail.ifBlank { "Google Account" }
                                    "${strings.driveConnectedLabel}: $email"
                                } else {
                                    strings.driveNotConnectedHint
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (settings.lastDriveBackupMs > 0L) {
                                val tsLabel = DateUtils.formatDateTime(
                                    settings.lastDriveBackupMs,
                                    calendarMode,
                                    usePersianDigits = isPersian
                                )
                                Text(
                                    text = "${strings.lastBackupLabel}: $tsLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(strings.backupScopeLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = settings.backupIncludeSms,
                        onClick = {
                            onBackupScopeChange(
                                !settings.backupIncludeSms,
                                settings.backupIncludeNotifications
                            )
                        },
                        label = { Text(strings.includeSmsLabel) },
                        modifier = Modifier.testTag("backup_scope_sms_chip")
                    )
                    FilterChip(
                        selected = settings.backupIncludeNotifications,
                        onClick = {
                            onBackupScopeChange(
                                settings.backupIncludeSms,
                                !settings.backupIncludeNotifications
                            )
                        },
                        label = { Text(strings.includeNotificationsLabel) },
                        modifier = Modifier.testTag("backup_scope_notifications_chip")
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Spacer(Modifier.height(10.dp))

                val isAutoBackup = settings.backupSyncMode == BackupSyncMode.AUTOMATIC
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.backupSyncModeLabel, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (isAutoBackup) {
                                strings.backupModeAutoSubtitle
                            } else {
                                strings.backupModeManualSubtitle
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isAutoBackup,
                        onCheckedChange = { enabled ->
                            onBackupSyncModeChange(
                                if (enabled) BackupSyncMode.AUTOMATIC else BackupSyncMode.MANUAL
                            )
                        },
                        modifier = Modifier.testTag("backup_auto_toggle")
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isAutoBackup,
                        onClick = { onBackupSyncModeChange(BackupSyncMode.MANUAL) },
                        label = { Text(strings.backupModeManual) },
                        modifier = Modifier.testTag("backup_mode_manual_chip")
                    )
                    FilterChip(
                        selected = isAutoBackup,
                        onClick = { onBackupSyncModeChange(BackupSyncMode.AUTOMATIC) },
                        label = { Text(strings.backupModeAutomatic) },
                        modifier = Modifier.testTag("backup_mode_auto_chip")
                    )
                }

                Spacer(Modifier.height(10.dp))

                if (!driveState.isConnected) {
                    Button(
                        onClick = onConnectGoogleDrive,
                        enabled = !driveState.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_drive_button")
                    ) {
                        if (driveState.isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.driveConnectButton)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onConnectDriveFallbackAccount,
                        enabled = !driveState.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_fallback_account_button")
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.driveFallbackAccountButton)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onToggleDriveTroubleshooting(!driveState.showTroubleshooting) },
                            modifier = Modifier.testTag("toggle_drive_troubleshooting_button")
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.driveTroubleshootingTitle)
                        }
                    }

                    AnimatedVisibility(visible = driveState.showTroubleshooting) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = strings.driveDiagnosticsHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                SelectionContainer {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "${strings.drivePackageLabel}: $apkPackageName",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                        )
                                        Text(
                                            text = "${strings.driveSha1Label}: $apkSha1Fingerprint",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        val diag = "Package: $apkPackageName\nSHA-1: $apkSha1Fingerprint\nRedirect URI: $apkPackageName:/oauth2redirect"
                                        clipboard.setText(AnnotatedString(diag))
                                        diagCopiedMessage = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (diagCopiedMessage) strings.copiedToClipboard else strings.driveCopyDiagInfo)
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

                                OutlinedTextField(
                                    value = customClientId,
                                    onValueChange = { customClientId = it },
                                    label = { Text(strings.driveCustomClientIdLabel) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onLaunchDriveBrowserPkce(customClientId) },
                                        enabled = customClientId.isNotBlank() && !driveState.isBusy,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.driveBrowserLoginButton, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }

                                if (customClientId.isNotBlank()) {
                                    OutlinedTextField(
                                        value = manualPkceCodeInput,
                                        onValueChange = { manualPkceCodeInput = it },
                                        label = { Text("OAuth Redirect URL / Code") },
                                        singleLine = true,
                                        trailingIcon = {
                                            TextButton(
                                                onClick = { onCompleteDriveBrowserPkce(manualPkceCodeInput) },
                                                enabled = manualPkceCodeInput.isNotBlank()
                                            ) {
                                                Text(strings.apply)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

                                OutlinedTextField(
                                    value = manualAccessTokenInput,
                                    onValueChange = { manualAccessTokenInput = it },
                                    label = { Text(strings.drivePasteTokenLabel) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = { onConnectDriveWithToken(manualAccessTokenInput) },
                                    enabled = manualAccessTokenInput.isNotBlank() && !driveState.isBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(strings.driveConnectWithTokenButton)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onBackupToGoogleDrive,
                            enabled = !driveState.isBusy && (settings.backupIncludeSms || settings.backupIncludeNotifications),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("drive_backup_now_button")
                        ) {
                            if (driveState.isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.backupNowButton)
                            }
                        }
                        OutlinedButton(
                            onClick = onRefreshDriveBackups,
                            enabled = !driveState.isBusy,
                            modifier = Modifier.testTag("drive_refresh_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = strings.refreshDriveBackups, modifier = Modifier.size(16.dp))
                        }
                        OutlinedButton(
                            onClick = onDisconnectGoogleDrive,
                            enabled = !driveState.isBusy,
                            modifier = Modifier.testTag("disconnect_drive_button")
                        ) {
                            Text(strings.driveDisconnectButton)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (driveState.backups.isEmpty() && !driveState.isBusy) {
                        Text(
                            text = strings.driveBackupsEmpty,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (backup in driveState.backups) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = backup.name,
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                                maxLines = 1
                                            )
                                            val ts = DateUtils.formatDateTime(
                                                backup.modifiedAtMs,
                                                calendarMode,
                                                usePersianDigits = isPersian
                                            )
                                            val smsN = DateUtils.formatNumber(backup.smsCount, isPersian)
                                            val notifN = DateUtils.formatNumber(backup.notificationCount, isPersian)
                                            Text(
                                                text = "$ts • $smsN ${strings.smsLabel} • $notifN ${strings.notificationLabel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onRestoreFromGoogleDrive(backup.id) },
                                            enabled = !driveState.isBusy && (settings.backupIncludeSms || settings.backupIncludeNotifications),
                                            modifier = Modifier.testTag("drive_restore_button")
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.restoreButton)
                                        }
                                        IconButton(
                                            onClick = { onDeleteDriveBackup(backup.id) },
                                            enabled = !driveState.isBusy
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportLocalBackup,
                        enabled = !driveState.isBusy && (settings.backupIncludeSms || settings.backupIncludeNotifications),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_local_backup_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.exportLocalBackup)
                    }
                    OutlinedButton(
                        onClick = onImportLocalBackup,
                        enabled = !driveState.isBusy && (settings.backupIncludeSms || settings.backupIncludeNotifications),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_local_backup_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.importLocalBackup)
                    }
                }

                driveState.statusMessage?.let { statusMsg ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (driveState.isError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = statusMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (driveState.isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 6. Appearance
            SettingsSectionCard(
                icon = Icons.Default.Palette,
                title = strings.sectionAppearance
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                        label = { Text(strings.themeSystem) }
                    )
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                        label = { Text(strings.themeLight) }
                    )
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) },
                        label = { Text(strings.themeDark) }
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.dynamicColorToggle, style = MaterialTheme.typography.titleSmall)
                            Text(
                                strings.dynamicColorSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                }
            }

            // 7. About / Release Version Info
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.sectionAbout, style = MaterialTheme.typography.titleSmall)
                    val verCode = DateUtils.formatNumber(BuildConfig.VERSION_CODE, isPersian)
                    Text(
                        text = "${strings.versionLabel} ${BuildConfig.VERSION_NAME} ($verCode)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CapabilityBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    supported: Boolean
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (supported) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (supported) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (supported) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
