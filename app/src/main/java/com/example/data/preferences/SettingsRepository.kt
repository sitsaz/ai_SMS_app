package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.localization.AppLanguage
import com.example.core.security.KeystoreCrypto
import com.example.data.local.AiProviderType
import com.example.data.local.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_sms_settings"
)

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val captureNotifications: Boolean = true,
    val aiProviderType: AiProviderType = AiProviderType.OPENAI_COMPATIBLE,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModelName: String = "gpt-4o-mini",
    val aiApiKey: String = ""
) {
    val isAiConfigured: Boolean
        get() = aiBaseUrl.isNotBlank() && aiModelName.isNotBlank() && aiApiKey.isNotBlank()
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CAPTURE_NOTIFICATIONS = booleanPreferencesKey("capture_notifications")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_API_KEY_ENC = stringPreferencesKey("ai_api_key_enc")
    }

    val settingsFlow: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        val provider = runCatching {
            AiProviderType.valueOf(prefs[Keys.AI_PROVIDER] ?: AiProviderType.OPENAI_COMPATIBLE.name)
        }.getOrDefault(AiProviderType.OPENAI_COMPATIBLE)

        val theme = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        val encKey = prefs[Keys.AI_API_KEY_ENC].orEmpty()
        val decryptedKey = if (encKey.isNotEmpty()) KeystoreCrypto.decrypt(encKey) else ""

        AppSettings(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            themeMode = theme,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            captureNotifications = prefs[Keys.CAPTURE_NOTIFICATIONS] ?: true,
            aiProviderType = provider,
            aiBaseUrl = prefs[Keys.AI_BASE_URL] ?: defaultBaseUrlFor(provider),
            aiModelName = prefs[Keys.AI_MODEL] ?: defaultModelFor(provider),
            aiApiKey = decryptedKey
        )
    }

    suspend fun getCurrentSettings(): AppSettings = settingsFlow.first()

    suspend fun setLanguage(language: AppLanguage) {
        context.appSettingsDataStore.edit { it[Keys.LANGUAGE] = language.code }
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

    suspend fun saveAiConfig(
        provider: AiProviderType,
        baseUrl: String,
        modelName: String,
        apiKey: String
    ) {
        val encrypted = KeystoreCrypto.encrypt(apiKey.trim())
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.AI_PROVIDER] = provider.name
            prefs[Keys.AI_BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[Keys.AI_MODEL] = modelName.trim()
            prefs[Keys.AI_API_KEY_ENC] = encrypted
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
