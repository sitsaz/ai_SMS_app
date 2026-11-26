package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AiSmsApplication
import com.example.core.localization.AppLanguage
import com.example.core.localization.CalendarMode
import com.example.core.localization.CalendarPreference
import com.example.core.localization.DateUtils
import com.example.data.drive.DriveAccountTokenOutcome
import com.example.data.drive.DriveBackupFileInfo
import com.example.data.drive.GoogleDriveAuthHelper
import com.example.data.drive.PkceSession
import com.example.data.local.AiProviderType
import com.example.data.local.BackupSyncMode
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
    data class Success(
        val preview: String,
        val supportsFileUpload: Boolean = false,
        val supportsImageInput: Boolean = false,
        val supportsSystemPrompt: Boolean = true,
        val supportsTemperature: Boolean = true,
        val supportsJsonMode: Boolean = false,
        val maxOutputTokens: Int? = null,
        val detectedContextLength: Int? = null,
        val capabilitySummary: String = ""
    ) : ConnectionTestState
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
    val isError: Boolean = false,
    val showTroubleshooting: Boolean = false
) {
    val isConnected: Boolean get() = accessToken.isNotBlank()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AiSmsApplication).container
    private val settingsRepo = container.settingsRepository
    private val aiClient = container.aiSearchClient
    private val backupManager = container.backupManager

    val apkPackageName: String = GoogleDriveAuthHelper.getAppPackageName(application)
    val apkSha1Fingerprint: String = GoogleDriveAuthHelper.getAppSigningSha1(application)

    private val _driveState = MutableStateFlow(DriveBackupUiState())
    val driveState: StateFlow<DriveBackupUiState> = _driveState.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .onEach { s ->
            DateUtils.updateCalendarMode(s.resolvedCalendarMode)
            _driveState.update { cur ->
                val hydratedEmail = if (cur.accountEmail.isBlank() && s.driveConnectedEmail.isNotBlank()) {
                    s.driveConnectedEmail
                } else {
                    cur.accountEmail
                }
                val hydratedToken = if (cur.accessToken.isBlank() && s.driveAccessToken.isNotBlank()) {
                    s.driveAccessToken
                } else {
                    cur.accessToken
                }
                cur.copy(accountEmail = hydratedEmail, accessToken = hydratedToken)
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

    init {
        viewModelScope.launch {
            val saved = settingsRepo.getCurrentSettings()
            if (saved.driveAccessToken.isNotBlank() || saved.driveConnectedEmail.isNotBlank()) {
                val freshToken = backupManager.obtainSilentAccessToken() ?: saved.driveAccessToken
                if (freshToken.isNotBlank()) {
                    _driveState.update {
                        it.copy(
                            accessToken = freshToken,
                            accountEmail = saved.driveConnectedEmail
                        )
                    }
                    val listRes = backupManager.listBackups(freshToken)
                    listRes.onSuccess { files ->
                        _driveState.update { it.copy(backups = files) }
                    }
                }
            }
        }
    }

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

    fun setSmsNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setSmsNotificationsEnabled(enabled) }
    }

    fun setShowNotificationPreviewText(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setShowNotificationPreviewText(enabled) }
    }

    fun setNotificationVibrate(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setNotificationVibrate(enabled) }
    }

    fun setSmsRingtone(uri: String, title: String) {
        viewModelScope.launch { settingsRepo.setSmsRingtone(uri, title) }
    }

    fun setBackupScope(includeSms: Boolean, includeNotifications: Boolean) {
        viewModelScope.launch {
            settingsRepo.setBackupScope(includeSms, includeNotifications)
        }
    }

    fun setBackupSyncMode(mode: BackupSyncMode) {
        viewModelScope.launch {
            backupManager.setSyncMode(mode)
        }
    }

    fun triggerAutoBackupIfDue() {
        viewModelScope.launch {
            backupManager.syncSilentlyIfDue(force = false)
        }
    }

    fun saveAiConfig(
        provider: AiProviderType,
        baseUrl: String,
        modelName: String,
        apiKey: String,
        contextLimitTokens: Int = 32_000
    ) {
        viewModelScope.launch {
            settingsRepo.saveAiConfig(provider, baseUrl, modelName, apiKey, contextLimitTokens)
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
        contextLimitTokens: Int,
        isPersian: Boolean
    ) {
        viewModelScope.launch {
            _testState.value = ConnectionTestState.Testing
            settingsRepo.saveAiConfig(provider, baseUrl, modelName, apiKey, contextLimitTokens)
            val testSettings = settingsRepo.getCurrentSettings()
            val result = aiClient.testConnectionWithCapabilities(testSettings, isPersian)
            _testState.value = result.fold(
                onSuccess = { report ->
                    settingsRepo.saveAiCapabilities(
                        supportsFileUpload = report.supportsFileUpload,
                        supportsImageInput = report.supportsImageInput,
                        supportsSystemPrompt = report.supportsSystemPrompt,
                        supportsTemperature = report.supportsTemperature,
                        supportsJsonMode = report.supportsJsonMode,
                        maxOutputTokens = report.maxOutputTokens,
                        detectedContextLimit = report.detectedContextLength,
                        detail = report.capabilitySummary
                    )
                    ConnectionTestState.Success(
                        preview = report.replyPreview,
                        supportsFileUpload = report.supportsFileUpload,
                        supportsImageInput = report.supportsImageInput,
                        supportsSystemPrompt = report.supportsSystemPrompt,
                        supportsTemperature = report.supportsTemperature,
                        supportsJsonMode = report.supportsJsonMode,
                        maxOutputTokens = report.maxOutputTokens,
                        detectedContextLength = report.detectedContextLength,
                        capabilitySummary = report.capabilitySummary
                    )
                },
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

    // Google Drive Backup & Restore + Device OAuth Fallbacks
    fun buildDriveAuthorizationRequest(): AuthorizationRequest =
        backupManager.buildAuthorizationRequest()

    fun setDriveTroubleshootingVisible(visible: Boolean) {
        _driveState.update { it.copy(showTroubleshooting = visible) }
    }

    fun saveDriveCustomClientId(clientId: String) {
        viewModelScope.launch {
            settingsRepo.setDriveCustomClientId(clientId)
        }
    }

    fun startBrowserPkceSession(customClientId: String): PkceSession? {
        viewModelScope.launch {
            settingsRepo.setDriveCustomClientId(customClientId)
        }
        return GoogleDriveAuthHelper.startBrowserPkceSession(getApplication(), customClientId)
    }

    fun startLoopbackBrowserPkce(
        customClientId: String,
        isPersian: Boolean,
        onLaunchAuthUrl: (String) -> Unit
    ) {
        val cleanId = customClientId.trim()
        if (cleanId.isBlank()) {
            onDriveAuthFailed(if (isPersian) "ابتدا Client ID را وارد کنید." else "Enter a Desktop/OAuth Client ID first.")
            return
        }
        viewModelScope.launch {
            settingsRepo.setDriveCustomClientId(cleanId)
            val socketRes = runCatching { GoogleDriveAuthHelper.openLoopbackSocket() }
            socketRes.onFailure { err ->
                onDriveAuthFailed(err.localizedMessage ?: "Cannot open local OAuth listener")
            }.onSuccess { serverSocket ->
                val redirectUri = "http://127.0.0.1:${serverSocket.localPort}"
                val session = GoogleDriveAuthHelper.startBrowserPkceSession(
                    context = getApplication(),
                    customClientId = cleanId,
                    overrideRedirectUri = redirectUri
                )
                if (session == null) {
                    runCatching { serverSocket.close() }
                    onDriveAuthFailed("Invalid Client ID")
                    return@launch
                }
                _driveState.update {
                    it.copy(
                        isBusy = true,
                        statusMessage = if (isPersian) {
                            "مرورگر باز شد؛ پس از تایید، اتصال به صورت خودکار تکمیل می‌شود..."
                        } else {
                            "Browser opened; waiting for Google OAuth redirect..."
                        },
                        isError = false
                    )
                }
                onLaunchAuthUrl(session.authUrl)
                val codeRes = GoogleDriveAuthHelper.awaitLoopbackPkceCallback(serverSocket, session.state)
                codeRes.onSuccess { code ->
                    val tokenRes = GoogleDriveAuthHelper.exchangePkceCodeForTokens(session, code)
                    tokenRes.onSuccess { (access, refresh) ->
                        onDriveAuthorized(accessToken = access, isPersian = isPersian, refreshToken = refresh)
                    }.onFailure { err ->
                        onDriveAuthFailed(err.localizedMessage ?: "Loopback token exchange failed")
                    }
                }.onFailure { err ->
                    onDriveAuthFailed(err.localizedMessage ?: "Loopback OAuth cancelled or timed out")
                }
            }
        }
    }

    fun completeBrowserPkce(session: PkceSession, rawUrlOrCode: String, isPersian: Boolean) {
        val code = extractAuthCode(rawUrlOrCode)
        if (code.isBlank()) {
            onDriveAuthFailed(
                if (isPersian) "کد تایید OAuth در آدرس یافت نشد." else "No OAuth authorization code found."
            )
            return
        }
        viewModelScope.launch {
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val tokenRes = GoogleDriveAuthHelper.exchangePkceCodeForTokens(session, code)
            tokenRes.onSuccess { (access, refresh) ->
                onDriveAuthorized(accessToken = access, isPersian = isPersian, refreshToken = refresh)
            }.onFailure { err ->
                onDriveAuthFailed(err.localizedMessage ?: "Browser OAuth exchange failed")
            }
        }
    }

    fun connectDriveWithGoogleAccount(
        accountEmail: String,
        isPersian: Boolean,
        onRecoverable: (Intent) -> Unit
    ) {
        viewModelScope.launch {
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            when (val outcome = GoogleDriveAuthHelper.requestTokenForAccount(getApplication(), accountEmail, isPersian)) {
                is DriveAccountTokenOutcome.Success -> {
                    onDriveAuthorized(
                        accessToken = outcome.accessToken,
                        isPersian = isPersian,
                        knownEmail = outcome.accountEmail
                    )
                }
                is DriveAccountTokenOutcome.Recoverable -> {
                    _driveState.update { it.copy(isBusy = false, accountEmail = outcome.accountEmail) }
                    onRecoverable(outcome.recoveryIntent)
                }
                is DriveAccountTokenOutcome.Failure -> {
                    _driveState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = outcome.message,
                            isError = true,
                            showTroubleshooting = it.showTroubleshooting || outcome.isConfigurationError
                        )
                    }
                }
            }
        }
    }

    fun connectWithManualAccessToken(rawToken: String, isPersian: Boolean) {
        val clean = rawToken.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
        if (clean.isBlank()) {
            onDriveAuthFailed(if (isPersian) "توکن معتبر وارد کنید." else "Please enter a valid OAuth access token.")
            return
        }
        onDriveAuthorized(accessToken = clean, isPersian = isPersian)
    }

    fun onDriveAuthorized(
        accessToken: String,
        isPersian: Boolean,
        refreshToken: String = "",
        knownEmail: String = ""
    ) {
        viewModelScope.launch {
            settingsRepo.setDriveTokens(accessToken, refreshToken)
            _driveState.update {
                it.copy(
                    accessToken = accessToken,
                    accountEmail = knownEmail.ifBlank { it.accountEmail },
                    isBusy = true,
                    statusMessage = null,
                    isError = false,
                    showTroubleshooting = false
                )
            }
            val fetchedEmail = backupManager.fetchAccountEmail(accessToken).getOrDefault("")
            val resolvedEmail = fetchedEmail.ifBlank { knownEmail }
            if (resolvedEmail.isNotBlank()) {
                settingsRepo.setDriveConnectionInfo(resolvedEmail)
            }
            val listRes = backupManager.listBackups(accessToken)
            _driveState.update { cur ->
                listRes.fold(
                    onSuccess = { files ->
                        cur.copy(
                            accountEmail = resolvedEmail.ifBlank { cur.accountEmail },
                            isBusy = false,
                            backups = files,
                            statusMessage = if (isPersian) "اتصال به Google Drive برقرار شد." else "Connected to Google Drive.",
                            isError = false
                        )
                    },
                    onFailure = { err ->
                        cur.copy(
                            accountEmail = resolvedEmail.ifBlank { cur.accountEmail },
                            isBusy = false,
                            statusMessage = err.localizedMessage ?: "Failed to list Drive backups",
                            isError = true
                        )
                    }
                )
            }
        }
    }

    fun onDriveAuthException(err: Throwable, isPersian: Boolean) {
        val (msg, isConfig) = GoogleDriveAuthHelper.describeDriveAuthException(getApplication(), err, isPersian)
        _driveState.update {
            it.copy(
                isBusy = false,
                statusMessage = msg,
                isError = true,
                showTroubleshooting = it.showTroubleshooting || isConfig
            )
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
            settingsRepo.setDriveTokens("", "")
            backupManager.setSyncMode(BackupSyncMode.MANUAL)
            _driveState.value = DriveBackupUiState(
                statusMessage = if (isPersian) "اتصال Google Drive قطع شد." else "Disconnected from Google Drive.",
                isError = false
            )
        }
    }

    fun refreshDriveBackups(isPersian: Boolean) {
        viewModelScope.launch {
            val token = resolveActiveDriveToken()
            if (token.isBlank()) {
                onDriveAuthFailed(if (isPersian) "ابتدا به Google Drive متصل شوید." else "Connect Google Drive first.")
                return@launch
            }
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = backupManager.listBackups(token)
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
        viewModelScope.launch {
            val token = resolveActiveDriveToken()
            if (token.isBlank()) {
                onDriveAuthFailed(if (isPersian) "ابتدا به Google Drive متصل شوید." else "Connect Google Drive first.")
                return@launch
            }
            val s = settingsRepo.getCurrentSettings()
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = backupManager.performSelectiveBackup(
                accessToken = token,
                includeSms = s.backupIncludeSms,
                includeNotifications = s.backupIncludeNotifications
            )
            if (res.isSuccess) {
                val uploaded = res.getOrThrow()
                val updatedList = backupManager.listBackups(token).getOrDefault(
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
        viewModelScope.launch {
            val token = resolveActiveDriveToken()
            if (token.isBlank()) return@launch
            val s = settingsRepo.getCurrentSettings()
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = backupManager.performSelectiveRestore(
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
        viewModelScope.launch {
            val token = resolveActiveDriveToken()
            if (token.isBlank()) return@launch
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = backupManager.deleteBackup(token, fileId)
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
        return backupManager.exportLocalBackupJson(
            includeSms = s.backupIncludeSms,
            includeNotifications = s.backupIncludeNotifications
        )
    }

    fun importLocalBackupJson(rawJson: String, isPersian: Boolean) {
        viewModelScope.launch {
            val s = settingsRepo.getCurrentSettings()
            _driveState.update { it.copy(isBusy = true, statusMessage = null, isError = false) }
            val res = backupManager.importLocalBackupJson(
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

    private suspend fun resolveActiveDriveToken(): String {
        val cur = _driveState.value.accessToken
        if (cur.isNotBlank()) return cur
        val refreshed = backupManager.obtainSilentAccessToken().orEmpty()
        if (refreshed.isNotBlank()) {
            _driveState.update { it.copy(accessToken = refreshed) }
        }
        return refreshed
    }

    private fun extractAuthCode(rawUrlOrCode: String): String {
        val trimmed = rawUrlOrCode.trim()
        if (trimmed.contains("://") || trimmed.contains("code=")) {
            val parsed = runCatching { Uri.parse(trimmed).getQueryParameter("code") }.getOrNull()
            if (!parsed.isNullOrBlank()) return parsed
        }
        return trimmed
    }
}
