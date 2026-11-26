package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AiSmsApplication
import com.example.core.localization.AppLanguage
import com.example.data.local.AiProviderType
import com.example.data.local.ThemeMode
import com.example.data.preferences.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val preview: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AiSmsApplication).container
    private val settingsRepo = container.settingsRepository
    private val aiClient = container.aiSearchClient

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepo.setLanguage(language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
    }

    fun setCaptureNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setCaptureNotifications(enabled) }
    }

    fun saveAiConfig(
        provider: AiProviderType,
        baseUrl: String,
        modelName: String,
        apiKey: String
    ) {
        viewModelScope.launch {
            settingsRepo.saveAiConfig(provider, baseUrl, modelName, apiKey)
        }
    }

    fun testAiConnection(
        provider: AiProviderType,
        baseUrl: String,
        modelName: String,
        apiKey: String,
        isPersian: Boolean
    ) {
        viewModelScope.launch {
            _testState.value = ConnectionTestState.Testing
            settingsRepo.saveAiConfig(provider, baseUrl, modelName, apiKey)
            val testSettings = settingsRepo.getCurrentSettings()
            val result = aiClient.testConnection(testSettings, isPersian)
            _testState.value = result.fold(
                onSuccess = { ConnectionTestState.Success(it) },
                onFailure = { ConnectionTestState.Error(it.localizedMessage ?: "Connection failed") }
            )
        }
    }

    fun clearTestState() {
        _testState.value = ConnectionTestState.Idle
    }
}
