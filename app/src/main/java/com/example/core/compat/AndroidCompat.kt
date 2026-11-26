package com.example.core.compat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AndroidCompat {
    const val SMS_CHANNEL_ID = "incoming_sms_channel"

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasCoreSmsPermissions(context: Context): Boolean {
        return hasPermission(context, android.Manifest.permission.READ_SMS) &&
            hasPermission(context, android.Manifest.permission.SEND_SMS) &&
            hasPermission(context, android.Manifest.permission.RECEIVE_SMS)
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

    fun ensureSmsNotificationChannel(context: Context, title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(SMS_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    SMS_CHANNEL_ID,
                    title,
                    NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun pendingIntentImmutableFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
