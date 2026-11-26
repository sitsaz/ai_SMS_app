package com.example.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.AiSmsApplication
import com.example.data.local.NotificationItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationArchiveService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

        val body = bigText.ifBlank { text }
        if (title.isBlank() && body.isBlank()) return

        // Skip silent ongoing progress bars unless they have meaningful body text
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary && body.isBlank()) return

        val appName = resolveAppName(pkg)
        val senderOrTitle = title.ifBlank { appName }
        val timestamp = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val key = sbn.key ?: "$pkg:${sbn.id}"

        val app = applicationContext as? AiSmsApplication ?: return
        serviceScope.launch {
            try {
                val settings = app.container.settingsRepository.getCurrentSettings()
                if (!settings.captureNotifications) return@launch

                app.container.notificationRepository.saveIfNotDuplicate(
                    NotificationItem(
                        notificationKey = key,
                        threadId = "$pkg:$senderOrTitle",
                        sender = senderOrTitle,
                        packageName = pkg,
                        appName = appName,
                        senderOrTitle = senderOrTitle,
                        body = body.ifBlank { senderOrTitle },
                        subText = subText,
                        timestamp = timestamp,
                        isRead = false
                    )
                )
            } catch (e: Exception) {
                Log.e("NotificationArchiveSvc", "Failed archiving notification", e)
            }
        }
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        } catch (_: Exception) {
            packageName
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
