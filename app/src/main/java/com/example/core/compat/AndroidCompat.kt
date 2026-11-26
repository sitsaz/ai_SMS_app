package com.example.core.compat

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AndroidCompat {
    const val SMS_CHANNEL_ID = "incoming_sms_channel"

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasReadSmsPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.READ_SMS)

    fun hasSendSmsPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.SEND_SMS)

    fun hasCoreSmsPermissions(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.READ_SMS) &&
            hasPermission(context, Manifest.permission.SEND_SMS) &&
            hasPermission(context, Manifest.permission.RECEIVE_SMS)
    }

    fun requiredMessagingPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    fun shouldShowSmsRationale(activity: Activity?): Boolean {
        if (activity == null) return false
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS) ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS) ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS)
    }

    fun createAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isDefaultSmsApp(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    fun createDefaultSmsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                @Suppress("DEPRECATION")
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun createNotificationListenerSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        } else {
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
    }

    @Suppress("DEPRECATION")
    fun getSmsManager(context: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
    }

    fun smsNotificationChannelId(version: Int): String {
        val safeVer = version.coerceAtLeast(1)
        return if (safeVer == 1) SMS_CHANNEL_ID else "${SMS_CHANNEL_ID}_v$safeVer"
    }

    fun ensureSmsNotificationChannel(
        context: Context,
        title: String,
        channelVersion: Int = 1,
        ringtoneUriString: String = "",
        vibrate: Boolean = true
    ): String {
        val channelId = smsNotificationChannelId(channelVersion)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return channelId
            // Clean up older versioned SMS channels so the user only sees the active one
            if (channelVersion > 1) {
                runCatching { manager.deleteNotificationChannel(SMS_CHANNEL_ID) }
                for (oldV in 2 until channelVersion) {
                    runCatching { manager.deleteNotificationChannel("${SMS_CHANNEL_ID}_v$oldV") }
                }
            }
            val existing = manager.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    title,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(vibrate)
                    val soundUri = when {
                        ringtoneUriString == RINGTONE_SILENT_VALUE -> null
                        ringtoneUriString.isNotBlank() -> runCatching { Uri.parse(ringtoneUriString) }.getOrNull()
                        else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    }
                    if (soundUri != null) {
                        val attrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        setSound(soundUri, attrs)
                    } else {
                        setSound(null, null)
                    }
                }
                manager.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    fun createSmsChannelSettingsIntent(context: Context, channelVersion: Int): Intent {
        val channelId = smsNotificationChannelId(channelVersion)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            createAppDetailsSettingsIntent(context)
        }
    }

    fun resolveRingtoneTitle(context: Context, uriString: String, isPersian: Boolean): String {
        if (uriString == RINGTONE_SILENT_VALUE) {
            return if (isPersian) "بی‌صدا" else "Silent"
        }
        if (uriString.isBlank()) {
            return if (isPersian) "صدای پیش‌فرض سیستم" else "System Default"
        }
        return runCatching {
            val uri = Uri.parse(uriString)
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: if (isPersian) "آهنگ انتخاب‌شده" else "Custom Tone"
    }

    const val RINGTONE_SILENT_VALUE = "__silent__"

    fun pendingIntentImmutableFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
