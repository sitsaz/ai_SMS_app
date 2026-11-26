package com.example.ui.screens

import android.os.Build
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.localization.AppLanguage
import com.example.core.localization.DateUtils
import com.example.core.localization.LocalIsPersian
import com.example.core.localization.LocalStrings
import com.example.data.local.AiProviderType
import com.example.data.local.ThemeMode
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsRepository
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.ConnectionTestState
import com.example.ui.viewmodel.SystemStatusState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    systemStatus: SystemStatusState,
    testState: ConnectionTestState,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onCaptureNotificationsChange: (Boolean) -> Unit,
    onRequestDefaultSms: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onSaveAiConfig: (AiProviderType, String, String, String) -> Unit,
    onTestAiConnection: (AiProviderType, String, String, String) -> Unit
) {
    val strings = LocalStrings.current
    val isPersian = LocalIsPersian.current

    var provider by remember(settings.aiProviderType) { mutableStateOf(settings.aiProviderType) }
    var baseUrl by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var modelName by remember(settings.aiModelName) { mutableStateOf(settings.aiModelName) }
    var apiKey by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var savedBanner by remember { mutableStateOf(false) }

    LaunchedEffect(settings.aiProviderType, settings.aiBaseUrl, settings.aiModelName, settings.aiApiKey) {
        provider = settings.aiProviderType
        baseUrl = settings.aiBaseUrl
        modelName = settings.aiModelName
        apiKey = settings.aiApiKey
    }

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

            // 3. AI API Configuration
            SettingsSectionCard(
                icon = Icons.Default.Key,
                title = strings.sectionAiConfiguration
            ) {
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
                                savedBanner = false
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
                        savedBanner = false
                    },
                    label = { Text(strings.aiBaseUrlLabel) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_base_url_input")
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = modelName,
                    onValueChange = {
                        modelName = it
                        savedBanner = false
                    },
                    label = { Text(strings.aiModelLabel) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_model_input")
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        savedBanner = false
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

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveAiConfig(provider, baseUrl, modelName, apiKey)
                            savedBanner = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_ai_config_button")
                    ) {
                        Text(strings.saveAiConfig)
                    }
                    OutlinedButton(
                        onClick = {
                            savedBanner = false
                            onTestAiConnection(provider, baseUrl, modelName, apiKey)
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

                if (savedBanner) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = strings.aiConfigSaved,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                when (testState) {
                    is ConnectionTestState.Success -> {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "OK: ${testState.preview}",
                                    style = MaterialTheme.typography.bodySmall
                                )
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
                    else -> Unit
                }
            }

            // 4. Appearance
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

            // 5. About / Release Version Info
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
