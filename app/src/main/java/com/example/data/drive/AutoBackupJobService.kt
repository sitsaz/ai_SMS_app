package com.example.data.drive

import android.app.job.JobParameters
import android.app.job.JobService
import com.example.AiSmsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AutoBackupJobService : JobService() {

    private var serviceScope: CoroutineScope? = null
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as? AiSmsApplication ?: return false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serviceScope = scope
        runningJob = scope.launch {
            val result = runCatching {
                app.container.backupManager.syncSilentlyIfDue(force = false)
            }.getOrNull()
            val needsRetry = result?.isFailure == true
            jobFinished(params, needsRetry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        runningJob?.cancel()
        serviceScope?.cancel()
        serviceScope = null
        return true
    }
}
