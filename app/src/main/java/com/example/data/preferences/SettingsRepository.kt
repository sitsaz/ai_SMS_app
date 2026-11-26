package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.localization.AppLanguage
import com.example.core.localization.CalendarMode
import com.example.core.localization.CalendarPreference
import com.example.core.localization.resolveIsPersian
import com.example.core.security.KeystoreCrypto
import com.example.data.local.AiProviderType
import com.example.data.local.BackupSyncMode
import com.example.data.local.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_sms_settings"
)

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val calendarPreference: CalendarPreference = CalendarPreference.AUTO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val captureNotifications: Boolean = true,
    val aiProviderType: AiProviderType = AiProviderType.OPENAI_COMPATIBLE,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModelName: String = "gpt-4o-mini",
    val aiApiKey: String = "",
    val aiContextLimitTokens: Int = 32_000,
    val aiMaxOutputTokens: Int = 4_096,
    val aiSupportsFileUpload: Boolean = false,
    val aiSupportsImageInput: Boolean = false,
    val aiSupportsSystemPrompt: Boolean = true,
    val aiSupportsTemperature: Boolean = true,
    val aiSupportsJsonMode: Boolean = false,
    val aiCapabilitiesChecked: Boolean = false,
    val aiCapabilitiesDetail: String = "",
    val smsNotificationsEnabled: Boolean = true,
    val showNotificationPreviewText: Boolean = true,
    val notificationVibrate: Boolean = true,
    val smsRingtoneUri: String = "",
    val smsRingtoneTitle: String = "",
    val notificationChannelVersion: Int = 1,
    val lastDefaultSmsBannerShownMs: Long = 0L,
    val backupIncludeSms: Boolean = true,
    val backupIncludeNotifications: Boolean = true,
    val backupSyncMode: BackupSyncMode = BackupSyncMode.MANUAL,
    val driveConnectedEmail: String = "",
    val driveAccessToken: String = "",
    val driveRefreshToken: String = "",
    val driveCustomClientId: String = "",
    val lastDriveBackupMs: Long = 0L
) {
    val isAiConfigured: Boolean
        get() = aiBaseUrl.isNotBlank() && aiModelName.isNotBlank() && aiApiKey.isNotBlank()

    val resolvedCalendarMode: CalendarMode
        get() = calendarPreference.resolve(language.resolveIsPersian())
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val CALENDAR_PREFERENCE = stringPreferencesKey("calendar_preference")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CAPTURE_NOTIFICATIONS = booleanPreferencesKey("capture_notifications")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_API_KEY_ENC = stringPreferencesKey("ai_api_key_enc")
        val AI_CONTEXT_LIMIT_TOKENS = intPreferencesKey("ai_context_limit_tokens")
        val AI_MAX_OUTPUT_TOKENS = intPreferencesKey("ai_max_output_tokens")
        val AI_SUPPORTS_FILE_UPLOAD = booleanPreferencesKey("ai_supports_file_upload")
        val AI_SUPPORTS_IMAGE_INPUT = booleanPreferencesKey("ai_supports_image_input")
        val AI_SUPPORTS_SYSTEM_PROMPT = booleanPreferencesKey("ai_supports_system_prompt")
        val AI_SUPPORTS_TEMPERATURE = booleanPreferencesKey("ai_supports_temperature")
        val AI_SUPPORTS_JSON_MODE = booleanPreferencesKey("ai_supports_json_mode")
        val AI_CAPABILITIES_CHECKED = booleanPreferencesKey("ai_capabilities_checked")
        val AI_CAPABILITIES_DETAIL = stringPreferencesKey("ai_capabilities_detail")
        val SMS_NOTIFICATIONS_ENABLED = booleanPreferencesKey("sms_notifications_enabled")
        val SHOW_NOTIFICATION_PREVIEW_TEXT = booleanPreferencesKey("show_notification_preview_text")
        val NOTIFICATION_VIBRATE = booleanPreferencesKey("notification_vibrate")
        val SMS_RINGTONE_URI = stringPreferencesKey("sms_ringtone_uri")
        val SMS_RINGTONE_TITLE = stringPreferencesKey("sms_ringtone_title")
        val NOTIFICATION_CHANNEL_VERSION = intPreferencesKey("notification_channel_version")
        val LAST_DEFAULT_SMS_BANNER_MS = longPreferencesKey("last_default_sms_banner_ms")
        val BACKUP_INCLUDE_SMS = booleanPreferencesKey("backup_include_sms")
        val BACKUP_INCLUDE_NOTIFICATIONS = booleanPreferencesKey("backup_include_notifications")
        val BACKUP_SYNC_MODE = stringPreferencesKey("backup_sync_mode")
        val DRIVE_CONNECTED_EMAIL = stringPreferencesKey("drive_connected_email")
        val DRIVE_ACCESS_TOKEN_ENC = stringPreferencesKey("drive_access_token_enc")
        val DRIVE_REFRESH_TOKEN_ENC = stringPreferencesKey("drive_refresh_token_enc")
        val DRIVE_CUSTOM_CLIENT_ID = stringPreferencesKey("drive_custom_client_id")
        val LAST_DRIVE_BACKUP_MS = longPreferencesKey("last_drive_backup_ms")
    }

    val settingsFlow: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        val provider = runCatching {
            AiProviderType.valueOf(prefs[Keys.AI_PROVIDER] ?: AiProviderType.OPENAI_COMPATIBLE.name)
        }.getOrDefault(AiProviderType.OPENAI_COMPATIBLE)

        val theme = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        val calPref = runCatching {
            CalendarPreference.valueOf(prefs[Keys.CALENDAR_PREFERENCE] ?: CalendarPreference.AUTO.name)
        }.getOrDefault(CalendarPreference.AUTO)

        val syncMode = runCatching {
            BackupSyncMode.valueOf(prefs[Keys.BACKUP_SYNC_MODE] ?: BackupSyncMode.MANUAL.name)
        }.getOrDefault(BackupSyncMode.MANUAL)

        val encKey = prefs[Keys.AI_API_KEY_ENC].orEmpty()
        val decryptedKey = if (encKey.isNotEmpty()) KeystoreCrypto.decrypt(encKey) else ""

        val encDriveToken = prefs[Keys.DRIVE_ACCESS_TOKEN_ENC].orEmpty()
        val decryptedDriveToken = if (encDriveToken.isNotEmpty()) KeystoreCrypto.decrypt(encDriveToken) else ""

        val encDriveRefresh = prefs[Keys.DRIVE_REFRESH_TOKEN_ENC].orEmpty()
        val decryptedDriveRefresh = if (encDriveRefresh.isNotEmpty()) KeystoreCrypto.decrypt(encDriveRefresh) else ""

        AppSettings(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            calendarPreference = calPref,
            themeMode = theme,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            captureNotifications = prefs[Keys.CAPTURE_NOTIFICATIONS] ?: true,
            aiProviderType = provider,
            aiBaseUrl = prefs[Keys.AI_BASE_URL] ?: defaultBaseUrlFor(provider),
            aiModelName = prefs[Keys.AI_MODEL] ?: defaultModelFor(provider),
            aiApiKey = decryptedKey,
            aiContextLimitTokens = (prefs[Keys.AI_CONTEXT_LIMIT_TOKENS] ?: 32_000).coerceIn(1_024, 2_000_000),
            aiMaxOutputTokens = (prefs[Keys.AI_MAX_OUTPUT_TOKENS] ?: 4_096).coerceIn(256, 131_072),
            aiSupportsFileUpload = prefs[Keys.AI_SUPPORTS_FILE_UPLOAD] ?: false,
            aiSupportsImageInput = prefs[Keys.AI_SUPPORTS_IMAGE_INPUT] ?: false,
            aiSupportsSystemPrompt = prefs[Keys.AI_SUPPORTS_SYSTEM_PROMPT] ?: true,
            aiSupportsTemperature = prefs[Keys.AI_SUPPORTS_TEMPERATURE] ?: true,
            aiSupportsJsonMode = prefs[Keys.AI_SUPPORTS_JSON_MODE] ?: false,
            aiCapabilitiesChecked = prefs[Keys.AI_CAPABILITIES_CHECKED] ?: false,
            aiCapabilitiesDetail = prefs[Keys.AI_CAPABILITIES_DETAIL].orEmpty(),
            smsNotificationsEnabled = prefs[Keys.SMS_NOTIFICATIONS_ENABLED] ?: true,
            showNotificationPreviewText = prefs[Keys.SHOW_NOTIFICATION_PREVIEW_TEXT] ?: true,
            notificationVibrate = prefs[Keys.NOTIFICATION_VIBRATE] ?: true,
            smsRingtoneUri = prefs[Keys.SMS_RINGTONE_URI].orEmpty(),
            smsRingtoneTitle = prefs[Keys.SMS_RINGTONE_TITLE].orEmpty(),
            notificationChannelVersion = (prefs[Keys.NOTIFICATION_CHANNEL_VERSION] ?: 1).coerceAtLeast(1),
            lastDefaultSmsBannerShownMs = prefs[Keys.LAST_DEFAULT_SMS_BANNER_MS] ?: 0L,
            backupIncludeSms = prefs[Keys.BACKUP_INCLUDE_SMS] ?: true,
            backupIncludeNotifications = prefs[Keys.BACKUP_INCLUDE_NOTIFICATIONS] ?: true,
            backupSyncMode = syncMode,
            driveConnectedEmail = prefs[Keys.DRIVE_CONNECTED_EMAIL].orEmpty(),
            driveAccessToken = decryptedDriveToken,
            driveRefreshToken = decryptedDriveRefresh,
            driveCustomClientId = prefs[Keys.DRIVE_CUSTOM_CLIENT_ID].orEmpty(),
            lastDriveBackupMs = prefs[Keys.LAST_DRIVE_BACKUP_MS] ?: 0L
        )
    }

    val calendarModeFlow: Flow<CalendarMode> = settingsFlow
        .map { it.resolvedCalendarMode }
        .distinctUntilChanged()

    suspend fun getCurrentSettings(): AppSettings = settingsFlow.first()

    suspend fun setLanguage(language: AppLanguage) {
        context.appSettingsDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setCalendarPreference(preference: CalendarPreference) {
        context.appSettingsDataStore.edit { it[Keys.CALENDAR_PREFERENCE] = preference.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCaptureNotifications(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.CAPTURE_NOTIFICATIONS] = enabled }
    }

    suspend fun setSmsNotificationsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.SMS_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setShowNotificationPreviewText(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.SHOW_NOTIFICATION_PREVIEW_TEXT] = enabled }
    }

    suspend fun setNotificationVibrate(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_VIBRATE] = enabled
            val curVer = (prefs[Keys.NOTIFICATION_CHANNEL_VERSION] ?: 1).coerceAtLeast(1)
            prefs[Keys.NOTIFICATION_CHANNEL_VERSION] = curVer + 1
        }
    }

    suspend fun setSmsRingtone(uri: String, title: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.SMS_RINGTONE_URI] = uri.trim()
            prefs[Keys.SMS_RINGTONE_TITLE] = title.trim()
            val curVer = (prefs[Keys.NOTIFICATION_CHANNEL_VERSION] ?: 1).coerceAtLeast(1)
            prefs[Keys.NOTIFICATION_CHANNEL_VERSION] = curVer + 1
        }
    }

    suspend fun setLastDefaultSmsBannerShownMs(timestampMs: Long) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.LAST_DEFAULT_SMS_BANNER_MS] = timestampMs
        }
    }

    suspend fun setBackupScope(includeSms: Boolean, includeNotifications: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.BACKUP_INCLUDE_SMS] = includeSms
            prefs[Keys.BACKUP_INCLUDE_NOTIFICATIONS] = includeNotifications
        }
    }

    suspend fun setBackupSyncMode(mode: BackupSyncMode) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.BACKUP_SYNC_MODE] = mode.name
        }
    }

    suspend fun setDriveConnectionInfo(email: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.DRIVE_CONNECTED_EMAIL] = email.trim()
        }
    }

    suspend fun setDriveTokens(accessToken: String, refreshToken: String = "") {
        val cleanAccess = accessToken.trim()
        val cleanRefresh = refreshToken.trim()
        val encAccess = if (cleanAccess.isNotEmpty()) KeystoreCrypto.encrypt(cleanAccess) else ""
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.DRIVE_ACCESS_TOKEN_ENC] = encAccess
            if (cleanRefresh.isNotEmpty()) {
                prefs[Keys.DRIVE_REFRESH_TOKEN_ENC] = KeystoreCrypto.encrypt(cleanRefresh)
            } else if (cleanAccess.isEmpty()) {
                prefs.remove(Keys.DRIVE_REFRESH_TOKEN_ENC)
            }
        }
    }

    suspend fun setDriveCustomClientId(clientId: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.DRIVE_CUSTOM_CLIENT_ID] = clientId.trim()
        }
    }

    suspend fun setLastDriveBackupMs(timestampMs: Long) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.LAST_DRIVE_BACKUP_MS] = timestampMs
        }
    }

    suspend fun saveAiConfig(
        provider: AiProviderType,
        baseUrl: String,
        modelName: String,
        apiKey: String,
        contextLimitTokens: Int? = null
    ) {
        val cleanKey = apiKey.trim()
        val encrypted = if (cleanKey.isNotEmpty()) KeystoreCrypto.encrypt(cleanKey) else ""
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.AI_PROVIDER] = provider.name
            prefs[Keys.AI_BASE_URL] = baseUrl.trim().trim('"', '\'', '`').trimEnd('/')
            prefs[Keys.AI_MODEL] = modelName.trim()
            prefs[Keys.AI_API_KEY_ENC] = encrypted
            if (contextLimitTokens != null) {
                prefs[Keys.AI_CONTEXT_LIMIT_TOKENS] = contextLimitTokens.coerceIn(1_024, 2_000_000)
            }
        }
    }

    suspend fun saveAiCapabilities(
        supportsFileUpload: Boolean,
        supportsImageInput: Boolean,
        supportsSystemPrompt: Boolean = true,
        supportsTemperature: Boolean = true,
        supportsJsonMode: Boolean = false,
        maxOutputTokens: Int? = null,
        detectedContextLimit: Int? = null,
        detail: String
    ) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.AI_SUPPORTS_FILE_UPLOAD] = supportsFileUpload
            prefs[Keys.AI_SUPPORTS_IMAGE_INPUT] = supportsImageInput
            prefs[Keys.AI_SUPPORTS_SYSTEM_PROMPT] = supportsSystemPrompt
            prefs[Keys.AI_SUPPORTS_TEMPERATURE] = supportsTemperature
            prefs[Keys.AI_SUPPORTS_JSON_MODE] = supportsJsonMode
            if (maxOutputTokens != null && maxOutputTokens >= 256) {
                prefs[Keys.AI_MAX_OUTPUT_TOKENS] = maxOutputTokens.coerceIn(256, 131_072)
            }
            if (detectedContextLimit != null && detectedContextLimit >= 1_024) {
                prefs[Keys.AI_CONTEXT_LIMIT_TOKENS] = detectedContextLimit.coerceIn(1_024, 2_000_000)
            }
            prefs[Keys.AI_CAPABILITIES_CHECKED] = true
            prefs[Keys.AI_CAPABILITIES_DETAIL] = detail.take(500)
        }
    }

    suspend fun clearAiConfig() {
        context.appSettingsDataStore.edit { prefs ->
            prefs.remove(Keys.AI_PROVIDER)
            prefs.remove(Keys.AI_BASE_URL)
            prefs.remove(Keys.AI_MODEL)
            prefs.remove(Keys.AI_API_KEY_ENC)
            prefs.remove(Keys.AI_MAX_OUTPUT_TOKENS)
            prefs.remove(Keys.AI_SUPPORTS_FILE_UPLOAD)
            prefs.remove(Keys.AI_SUPPORTS_IMAGE_INPUT)
            prefs.remove(Keys.AI_SUPPORTS_SYSTEM_PROMPT)
            prefs.remove(Keys.AI_SUPPORTS_TEMPERATURE)
            prefs.remove(Keys.AI_SUPPORTS_JSON_MODE)
            prefs.remove(Keys.AI_CAPABILITIES_CHECKED)
            prefs.remove(Keys.AI_CAPABILITIES_DETAIL)
        }
    }

    companion object {
        fun defaultBaseUrlFor(provider: AiProviderType): String = when (provider) {
            AiProviderType.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
            AiProviderType.GEMINI_REST -> "https://generativelanguage.googleapis.com/v1beta"
            AiProviderType.CUSTOM -> ""
        }

        fun defaultModelFor(provider: AiProviderType): String = when (provider) {
            AiProviderType.OPENAI_COMPATIBLE -> "gpt-4o-mini"
            AiProviderType.GEMINI_REST -> "gemini-2.5-flash"
            AiProviderType.CUSTOM -> ""
        }
    }
}
