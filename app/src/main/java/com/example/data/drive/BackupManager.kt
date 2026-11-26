package com.example.data.drive

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.example.data.local.BackupSyncMode
import com.example.data.preferences.SettingsRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class BackupSyncResult(
    val uploadedFile: DriveBackupFileInfo?,
    val skippedReason: String? = null
)

class BackupManager(
    private val context: Context,
    private val googleDriveBackupManager: GoogleDriveBackupManager,
    private val settingsRepository: SettingsRepository
) {

    fun buildAuthorizationRequest(): AuthorizationRequest =
        googleDriveBackupManager.buildAuthorizationRequest()

    suspend fun fetchAccountEmail(accessToken: String): Result<String> =
        googleDriveBackupManager.fetchAccountEmail(accessToken)

    suspend fun listBackups(accessToken: String): Result<List<DriveBackupFileInfo>> =
        googleDriveBackupManager.listBackups(accessToken)

    suspend fun performSelectiveBackup(
        accessToken: String,
        includeSms: Boolean? = null,
        includeNotifications: Boolean? = null
    ): Result<DriveBackupFileInfo> {
        val settings = settingsRepository.getCurrentSettings()
        val smsFlag = includeSms ?: settings.backupIncludeSms
        val notifFlag = includeNotifications ?: settings.backupIncludeNotifications
        val res = googleDriveBackupManager.createAndUploadBackup(
            accessToken = accessToken,
            includeSms = smsFlag,
            includeNotifications = notifFlag
        )
        if (res.isSuccess) {
            settingsRepository.setLastDriveBackupMs(System.currentTimeMillis())
        }
        return res
    }

    suspend fun performSelectiveRestore(
        accessToken: String,
        fileId: String,
        restoreSms: Boolean? = null,
        restoreNotifications: Boolean? = null
    ): Result<RestoreSummary> {
        val settings = settingsRepository.getCurrentSettings()
        return googleDriveBackupManager.restoreBackup(
            accessToken = accessToken,
            fileId = fileId,
            restoreSms = restoreSms ?: settings.backupIncludeSms,
            restoreNotifications = restoreNotifications ?: settings.backupIncludeNotifications
        )
    }

    suspend fun deleteBackup(accessToken: String, fileId: String): Result<Unit> =
        googleDriveBackupManager.deleteBackup(accessToken, fileId)

    suspend fun exportLocalBackupJson(
        includeSms: Boolean? = null,
        includeNotifications: Boolean? = null
    ): String {
        val settings = settingsRepository.getCurrentSettings()
        return googleDriveBackupManager.exportLocalBackupJson(
            includeSms = includeSms ?: settings.backupIncludeSms,
            includeNotifications = includeNotifications ?: settings.backupIncludeNotifications
        )
    }

    suspend fun importLocalBackupJson(
        rawJson: String,
        restoreSms: Boolean? = null,
        restoreNotifications: Boolean? = null
    ): Result<RestoreSummary> {
        val settings = settingsRepository.getCurrentSettings()
        return googleDriveBackupManager.importLocalBackupJson(
            rawJson = rawJson,
            restoreSms = restoreSms ?: settings.backupIncludeSms,
            restoreNotifications = restoreNotifications ?: settings.backupIncludeNotifications
        )
    }

    suspend fun setSyncMode(mode: BackupSyncMode) {
        settingsRepository.setBackupSyncMode(mode)
        scheduleOrCancelAutoBackup(mode)
    }

    suspend fun ensureScheduleMatchesSettings() {
        val settings = settingsRepository.getCurrentSettings()
        scheduleOrCancelAutoBackup(settings.backupSyncMode)
    }

    fun scheduleOrCancelAutoBackup(mode: BackupSyncMode) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        if (mode == BackupSyncMode.MANUAL) {
            scheduler.cancel(JOB_ID_AUTO_BACKUP)
            return
        }

        val existing = scheduler.allPendingJobs.any { it.id == JOB_ID_AUTO_BACKUP }
        if (existing) return

        val component = ComponentName(context, AutoBackupJobService::class.java)
        val builder = JobInfo.Builder(JOB_ID_AUTO_BACKUP, component)
            .setPeriodic(TimeUnit.HOURS.toMillis(AUTO_BACKUP_INTERVAL_HOURS))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setRequiresBatteryNotLow(true)
        }
        scheduler.schedule(builder.build())
    }

    suspend fun syncSilentlyIfDue(force: Boolean = false): Result<BackupSyncResult> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getCurrentSettings()
        if (!force && settings.backupSyncMode != BackupSyncMode.AUTOMATIC) {
            return@withContext Result.success(BackupSyncResult(null, "Manual backup mode"))
        }
        if (!settings.backupIncludeSms && !settings.backupIncludeNotifications) {
            return@withContext Result.success(BackupSyncResult(null, "No backup scope selected"))
        }
        val elapsed = System.currentTimeMillis() - settings.lastDriveBackupMs
        if (!force && settings.lastDriveBackupMs > 0L && elapsed < AUTO_BACKUP_MIN_INTERVAL_MS) {
            return@withContext Result.success(BackupSyncResult(null, "Recently backed up"))
        }

        val token = obtainSilentAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.success(BackupSyncResult(null, "Google Drive authorization required"))
        }

        performSelectiveBackup(
            accessToken = token,
            includeSms = settings.backupIncludeSms,
            includeNotifications = settings.backupIncludeNotifications
        ).map { info ->
            BackupSyncResult(uploadedFile = info)
        }
    }

    suspend fun obtainSilentAccessToken(): String? {
        val settings = settingsRepository.getCurrentSettings()

        // 1. Try Play Services Identity Authorization
        val identityToken = tryIdentitySilentToken()
        if (!identityToken.isNullOrBlank()) {
            settingsRepository.setDriveTokens(identityToken)
            return identityToken
        }

        // 2. Try GoogleAuthUtil for already-connected Google account
        if (settings.driveConnectedEmail.isNotBlank()) {
            val utilToken = GoogleDriveAuthHelper.trySilentGoogleAuthUtilToken(context, settings.driveConnectedEmail)
            if (!utilToken.isNullOrBlank()) {
                settingsRepository.setDriveTokens(utilToken)
                return utilToken
            }
        }

        // 3. Try refreshing OAuth token if PKCE refresh token & client ID were configured
        if (settings.driveCustomClientId.isNotBlank() && settings.driveRefreshToken.isNotBlank()) {
            val refreshed = GoogleDriveAuthHelper.refreshAccessToken(
                clientId = settings.driveCustomClientId,
                refreshToken = settings.driveRefreshToken
            ).getOrNull()
            if (!refreshed.isNullOrBlank()) {
                settingsRepository.setDriveTokens(refreshed)
                return refreshed
            }
        }

        // 4. Fall back to stored encrypted token
        return settings.driveAccessToken.takeIf { it.isNotBlank() }
    }

    private suspend fun tryIdentitySilentToken(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            Identity.getAuthorizationClient(context)
                .authorize(buildAuthorizationRequest())
                .addOnSuccessListener { result ->
                    if (!cont.isActive) return@addOnSuccessListener
                    val token = if (!result.hasResolution()) result.accessToken else null
                    cont.resume(token)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }.onFailure {
            if (cont.isActive) cont.resume(null)
        }
    }

    companion object {
        const val JOB_ID_AUTO_BACKUP = 4107
        const val AUTO_BACKUP_INTERVAL_HOURS = 24L
        val AUTO_BACKUP_MIN_INTERVAL_MS: Long = TimeUnit.HOURS.toMillis(12)
    }
}
