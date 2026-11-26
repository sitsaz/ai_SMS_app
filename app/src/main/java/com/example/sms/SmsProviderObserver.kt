package com.example.sms

import android.Manifest
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.example.core.compat.AndroidCompat
import com.example.data.sms.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Monitors Android's Telephony.Sms ContentProvider in real time and syncs
 * incoming and outgoing SMS messages into the Room database immediately.
 */
class SmsProviderObserver(
    private val context: Context,
    private val smsRepository: SmsRepository
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    @Volatile
    private var isRegistered: Boolean = false

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    @Synchronized
    fun registerIfPermitted(): Boolean {
        if (isRegistered) return true
        if (!AndroidCompat.hasPermission(context, Manifest.permission.READ_SMS)) {
            return false
        }
        return runCatching {
            context.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                this
            )
            isRegistered = true
            true
        }.onFailure { err ->
            Log.e("SmsProviderObserver", "Failed to register SMS ContentObserver", err)
        }.getOrDefault(false)
    }

    @Synchronized
    fun unregister() {
        if (!isRegistered) return
        runCatching {
            context.contentResolver.unregisterContentObserver(this)
        }
        isRegistered = false
        debounceJob?.cancel()
    }

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        scheduleIncrementalSync()
    }

    internal fun scheduleIncrementalSync(delayMs: Long = 350L) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            runCatching {
                smsRepository.syncRecentChanges(limit = 80)
                _lastSyncTimestamp.value = System.currentTimeMillis()
            }.onFailure { err ->
                Log.e("SmsProviderObserver", "Real-time SMS sync failed", err)
            }
        }
    }
}
