package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AiSmsApplication
import com.example.core.localization.AppLanguage
import com.example.core.localization.CalendarMode
import com.example.core.localization.CalendarPreference
import com.example.core.localization.DateUtils
import com.example.data.drive.DriveBackupFileInfo
import com.example.data.local.AiProviderType
import com.example.data.local.ThemeMode
import com.example.data.preferences.AppSettings
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val preview: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}

sealed interface ModelsFetchState {
    data object Idle : ModelsFetchState
    data object Loading : ModelsFetchState
    data class Success(val models: List<String>) : ModelsFetchState
    data class Error(val message: String) : ModelsFetchState
}

data class DriveBackupUiState(
    val accessToken: String = "",
    val accountEmail: String = "",
    val isBusy: Boolean = false,
    val backups: List<DriveBackupFileInfo> = emptyList(),
    val statusMessage: String? = null,
    val isError: Boolean = false
) {
    val isConnected: Boolean get() = accessToken.isNotBlank()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AiSmsApplication).container
    private val settingsRepo = container.settingsRepository
    private val aiClient = container.aiSearchClient
    private val driveManager = container.googleDriveBackupManager

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .onEach { s ->
            DateUtils.updateCalendarMode(s.resolvedCalendarMode)
            if (s.driveConnectedEmail.isNotBlank() && _driveState.value.accountEmail.isBlank()) {
                _driveState.update { it.copy(accountEmail = s.driveConnectedEmail) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val calendarMode: StateFlow<CalendarMode> = settingsRepo.calendarModeFlow
        .onEach { DateUtils.updateCalendarMode(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings().resolvedCalendarMode
        )

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    private val _modelsState = MutableStateFlow<ModelsFetchState>(ModelsFetchState.Idle)
    val modelsState: StateFlow<ModelsFetchState> = _modelsState.asStateFlow()

    private val _driveState = MutableStateFlow(DriveBackupUiState())
    val driveState: StateFlow<DriveBackupUiState> = _driveState.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepo.setLanguage(language) }
    }

    fun setCalendarPreference(preference: CalendarPreference) {
        viewModelScope.launch { settingsRepo.setCalendarPreference(preference) }
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

    fun setBackupScope(includeSms: Boolean, includeNotifications: Boolean) {
        viewModelScope.launch {
            settingsRepo.setBackupScope(includeSms, includeNotifications)
        }
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

    fun clearAiConfig() {
        viewModelScope.launch {
            settingsRepo.clearAiConfig()
            _testState.value = ConnectionTestState.Idle
            _modelsState.value = ModelsFetchState.Idle
        }
    }

    fun fetchModels(
        provider: AiProviderType,
        baseUrl: String,
        apiKey: String,
        isPersian: Boolean
    ) {
        viewModelScope.launch {
            _modelsState.value = ModelsFetchState.Loading
            val result = aiClient.fetchAvailableModels(provider, baseUrl, apiKey, isPersian)
            _modelsState.value = result.fold(
                onSuccess = { models ->
                    if (models.isEmpty()) {
                        ModelsFetchState.Error(
                            if (isPersian) "مدلی در پاسخ سرور یافت نشد." else "No models returned by endpoint."
                        )
                    } else {
                        ModelsFetchState.Success(models)
                    }
                },
                onFailure = { err ->
                    ModelsFetchState.Error(err.localizedMessage ?: "Failed to load models")
                }
            )
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

    fun clearModelsState() {
        _modelsState.value = ModelsFetchState.Idle
    }

    // Google Drive Backup & Restore
    fun buildDriveAuthorizationRequest(): AuthorizationRequest =
        driveManager.buildAuthorizationRequest()

    fun onDriveAuthorized(accessToken: String, isPersian: Boolean) {
        viewModelScope.launch {
            _driveState.update {
                it.copy(
                    accessToken = accessToken,
                    isBusy = true,
                    statusMessage = null,
                    isError = false
                )
            }
            val email = driveManager.fetchAccountEmail(accessToken).getOrDefault("")
            if (email.isNotBlank()) {
                settingsRepo.setDriveConnectionInfo(email)
            }
            val listRes = driveManager.listBackups(accessToken)
            _driveState.update { cur ->
                listRes.fold(
                    onSuccess = { files ->
                        cur.copy(
                            accountEmail = email.ifBlank { cur.accountEmail },
                            isBusy = false,
                            backups = files,
                            statusMessage = if (isPersian) "اتصال به Google Drive برقرار شد." else "Connected to Google Drive.",
                            isError = false
                        )
                    },
                    onFailure = { err ->
                        cur.copy(
                            accountEmail = email.ifBlank { cur.accountEmail },
                            isBusy = false,
                            statusMessage = err.localizedMessage ?: "Failed to list Drive backups",
                            isError = true
                        )
                    }
                )
            }
        }
    }

    fun onDriveAuthFailed(message: String) {
        _driveState.update {
            it.copy(isBusy = false, statusMessage = message, isError = true)
        }
    }

    fun disconnectDrive(isPersian: Boolean) {
        viewModelScope.launch {
            settingsRepo.setDriveConnectionInfo("")
            _driveState.value = DriveBackupUiState(
                statusMessage = if (isPersian) "اتصال Google Drive قطع شد." else "Disconnected from Google Drive.",
                isError = false
            )
        }
    }

    fun refreshDriveBackups(isPersian: Boolean) {
        val token = _driveState.value.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = driveManager.listBackups(token)
            _driveState.update { cur ->
                res.fold(
                    onSuccess = { files ->
                        cur.copy(
                            isBusy = false,
                            backups = files,
                            statusMessage = if (isPersian) {
                                "${DateUtils.formatNumber(files.size, true)} فایل بکاپ یافت شد."
                            } else {
                                "Found ${files.size} backup(s)."
                            },
                            isError = false
                        )
                    },
                    onFailure = { err ->
                        cur.copy(isBusy = false, statusMessage = err.localizedMessage, isError = true)
                    }
                )
            }
        }
    }

    fun backupToGoogleDrive(isPersian: Boolean) {
        val token = _driveState.value.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            val s = settingsRepo.getCurrentSettings()
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = driveManager.createAndUploadBackup(
                accessToken = token,
                includeSms = s.backupIncludeSms,
                includeNotifications = s.backupIncludeNotifications
            )
            if (res.isSuccess) {
                val uploaded = res.getOrThrow()
                settingsRepo.setLastDriveBackupMs(System.currentTimeMillis())
                val updatedList = driveManager.listBackups(token).getOrDefault(
                    listOf(uploaded) + _driveState.value.backups
                )
                val smsN = DateUtils.formatNumber(uploaded.smsCount, isPersian)
                val notifN = DateUtils.formatNumber(uploaded.notificationCount, isPersian)
                _driveState.update {
                    it.copy(
                        isBusy = false,
                        backups = updatedList,
                        statusMessage = if (isPersian) {
                            "بکاپ با موفقیت در Drive ذخیره شد ($smsN پیامک • $notifN اعلان)."
                        } else {
                            "Backed up to Drive ($smsN SMS • $notifN alerts)."
                        },
                        isError = false
                    )
                }
            } else {
                _driveState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = res.exceptionOrNull()?.localizedMessage ?: "Drive backup failed",
                        isError = true
                    )
                }
            }
        }
    }

    fun restoreFromGoogleDrive(fileId: String, isPersian: Boolean) {
        val token = _driveState.value.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            val s = settingsRepo.getCurrentSettings()
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = driveManager.restoreBackup(
                accessToken = token,
                fileId = fileId,
                restoreSms = s.backupIncludeSms,
                restoreNotifications = s.backupIncludeNotifications
            )
            _driveState.update { cur ->
                res.fold(
                    onSuccess = { summary ->
                        val smsN = DateUtils.formatNumber(summary.restoredSmsCount, isPersian)
                        val notifN = DateUtils.formatNumber(summary.restoredNotificationCount, isPersian)
                        cur.copy(
                            isBusy = false,
                            statusMessage = if (isPersian) {
                                "بازگردانی انجام شد: $smsN پیامک و $notifN اعلان اضافه شد."
                            } else {
                                "Restored $smsN SMS and $notifN alerts."
                            },
                            isError = false
                        )
                    },
                    onFailure = { err ->
                        cur.copy(isBusy = false, statusMessage = err.localizedMessage, isError = true)
                    }
                )
            }
        }
    }

    fun deleteDriveBackup(fileId: String, isPersian: Boolean) {
        val token = _driveState.value.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = driveManager.deleteBackup(token, fileId)
            _driveState.update { cur ->
                res.fold(
                    onSuccess = {
                        cur.copy(
                            isBusy = false,
                            backups = cur.backups.filterNot { it.id == fileId },
                            statusMessage = if (isPersian) "بکاپ حذف شد." else "Backup deleted from Drive.",
                            isError = false
                        )
                    },
                    onFailure = { err ->
                        cur.copy(isBusy = false, statusMessage = err.localizedMessage, isError = true)
                    }
                )
            }
        }
    }

    suspend fun exportLocalBackupJson(): String {
        val s = settingsRepo.getCurrentSettings()
        return driveManager.exportLocalBackupJson(
            includeSms = s.backupIncludeSms,
            includeNotifications = s.backupIncludeNotifications
        )
    }

    fun importLocalBackupJson(rawJson: String, isPersian: Boolean) {
        viewModelScope.launch {
            val s = settingsRepo.getCurrentSettings()
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = driveManager.importLocalBackupJson(
                rawJson = rawJson,
                restoreSms = s.backupIncludeSms,
                restoreNotifications = s.backupIncludeNotifications
            )
            _driveState.update { cur ->
                res.fold(
                    onSuccess = { summary ->
                        val smsN = DateUtils.formatNumber(summary.restoredSmsCount, isPersian)
                        val notifN = DateUtils.formatNumber(summary.restoredNotificationCount, isPersian)
                        cur.copy(
                            isBusy = false,
                            statusMessage = if (isPersian) {
                                "بازگردانی از فایل انجام شد: $smsN پیامک و $notifN اعلان."
                            } else {
                                "Imported $smsN SMS and $notifN alerts from file."
                            },
                            isError = false
                        )
                    },
                    onFailure = { err ->
                        cur.copy(isBusy = false, statusMessage = err.localizedMessage, isError = true)
                    }
                )
            }
        }
    }

    fun setLocalDriveMessage(message: String, isError: Boolean = false) {
        _driveState.update { it.copy(statusMessage = message, isError = isError) }
    }
}
