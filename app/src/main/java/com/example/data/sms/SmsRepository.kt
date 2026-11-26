package com.example.data.sms

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.compat.AndroidCompat
import com.example.data.local.ConversationRow
import com.example.data.local.DeliveryStatus
import com.example.data.local.PinnedThreadEntity
import com.example.data.local.SmsDao
import com.example.data.local.SmsMessage
import com.example.data.preferences.SettingsRepository
import com.example.sms.SmsSenderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SmsRepository(
    private val context: Context,
    private val smsDao: SmsDao,
    private val contactsRepository: ContactsRepository,
    private val senderManager: SmsSenderManager,
    private val settingsRepository: SettingsRepository
) {

    fun observeConversations(): Flow<List<ConversationRow>> = smsDao.observeConversations()

    fun observeThreadMessages(threadId: Long): Flow<List<SmsMessage>> =
        smsDao.observeThreadMessages(threadId)

    fun observePinnedMessagesInThread(threadId: Long): Flow<List<SmsMessage>> =
        smsDao.observePinnedMessagesInThread(threadId)

    suspend fun syncFromSystemInbox(): Int = withContext(Dispatchers.IO) {
        if (!AndroidCompat.hasPermission(context, Manifest.permission.READ_SMS)) {
            return@withContext 0
        }
        val contactMap = contactsRepository.buildLookupMap()
        val imported = mutableListOf<SmsMessage>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS
        )

        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                val statusIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)

                while (cursor.moveToNext()) {
                    val sysId = cursor.getLong(idIdx)
                    val threadId = cursor.getLong(threadIdx)
                    val address = cursor.getString(addressIdx)?.trim().orEmpty().ifBlank { "Unknown" }
                    val normalized = ContactsRepository.normalizeNumberKey(address)
                    val body = cursor.getString(bodyIdx).orEmpty()
                    val date = cursor.getLong(dateIdx)
                    val type = cursor.getInt(typeIdx)
                    val isOutgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                        type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                        type == Telephony.Sms.MESSAGE_TYPE_QUEUED
                    val isRead = cursor.getInt(readIdx) == 1 || isOutgoing
                    val statusVal = cursor.getInt(statusIdx)
                    val deliveryStatus = when {
                        !isOutgoing -> DeliveryStatus.NONE
                        type == Telephony.Sms.MESSAGE_TYPE_FAILED -> DeliveryStatus.FAILED
                        statusVal == Telephony.Sms.STATUS_COMPLETE -> DeliveryStatus.DELIVERED
                        statusVal == Telephony.Sms.STATUS_FAILED -> DeliveryStatus.FAILED
                        statusVal == Telephony.Sms.STATUS_PENDING -> DeliveryStatus.SENDING
                        else -> DeliveryStatus.SENT
                    }

                    val resolvedName = contactMap[normalized]
                    imported.add(
                        SmsMessage(
                            systemSmsId = sysId,
                            threadId = threadId,
                            sender = resolvedName ?: address,
                            address = address,
                            normalizedAddress = normalized,
                            contactName = resolvedName,
                            body = body,
                            timestamp = date,
                            isOutgoing = isOutgoing,
                            deliveryStatus = deliveryStatus.name,
                            isRead = isRead
                        )
                    )
                }
            }
        }.onFailure { err ->
            Log.e("SmsRepository", "Error querying system SMS", err)
        }

        if (imported.isNotEmpty()) {
            // Preserve locally tracked DELIVERED statuses where system SMS provider hasn't updated
            for (batch in imported.chunked(300)) {
                smsDao.upsertAll(batch)
            }
        }
        imported.size
    }

    suspend fun resolveOrCreateThreadId(address: String): Long = withContext(Dispatchers.IO) {
        val normalized = ContactsRepository.normalizeNumberKey(address)
        val existing = smsDao.findLatestByNormalizedAddress(normalized)
        if (existing != null) return@withContext existing.threadId

        val systemThread = runCatching {
            Telephony.Threads.getOrCreateThreadId(context, address)
        }.getOrNull()
        if (systemThread != null && systemThread > 0L) {
            return@withContext systemThread
        }
        smsDao.nextSyntheticThreadId()
    }

    suspend fun sendSmsMessage(
        address: String,
        body: String,
        existingThreadId: Long? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        val cleanAddress = address.trim()
        val cleanBody = body.trim()
        if (cleanAddress.isEmpty() || cleanBody.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty address or body"))
        }

        val now = System.currentTimeMillis()
        val threadId = existingThreadId ?: resolveOrCreateThreadId(cleanAddress)
        val normalized = ContactsRepository.normalizeNumberKey(cleanAddress)
        val contactName = contactsRepository.buildLookupMap()[normalized]

        var systemSmsId: Long? = null
        if (AndroidCompat.isDefaultSmsApp(context)) {
            runCatching {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, cleanAddress)
                    put(Telephony.Sms.BODY, cleanBody)
                    put(Telephony.Sms.DATE, now)
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
                val uri: Uri? = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                systemSmsId = uri?.lastPathSegment?.toLongOrNull()
            }
        }

        val localId = smsDao.insertMessage(
            SmsMessage(
                systemSmsId = systemSmsId,
                threadId = threadId,
                sender = contactName ?: cleanAddress,
                address = cleanAddress,
                normalizedAddress = normalized,
                contactName = contactName,
                body = cleanBody,
                timestamp = now,
                isOutgoing = true,
                deliveryStatus = DeliveryStatus.SENDING.name,
                isRead = true
            )
        )

        val sendResult = senderManager.sendSms(localId, cleanAddress, cleanBody)
        if (sendResult.isFailure) {
            smsDao.updateDeliveryStatus(localId, DeliveryStatus.FAILED.name)
            return@withContext Result.failure(sendResult.exceptionOrNull() ?: Exception("SMS send failed"))
        }

        Result.success(threadId)
    }

    suspend fun handleIncomingSms(
        address: String,
        body: String,
        timestamp: Long
    ) = withContext(Dispatchers.IO) {
        val cleanAddress = address.trim().ifBlank { "Unknown" }
        val normalized = ContactsRepository.normalizeNumberKey(cleanAddress)
        val threadId = resolveOrCreateThreadId(cleanAddress)
        val contactName = contactsRepository.buildLookupMap()[normalized]

        var systemSmsId: Long? = null
        if (AndroidCompat.isDefaultSmsApp(context)) {
            runCatching {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, cleanAddress)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, timestamp)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
                val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                systemSmsId = uri?.lastPathSegment?.toLongOrNull()
            }
        }

        val localId = smsDao.insertMessage(
            SmsMessage(
                systemSmsId = systemSmsId,
                threadId = threadId,
                sender = contactName ?: cleanAddress,
                address = cleanAddress,
                normalizedAddress = normalized,
                contactName = contactName,
                body = body,
                timestamp = timestamp,
                isOutgoing = false,
                deliveryStatus = DeliveryStatus.NONE.name,
                isRead = false
            )
        )

        postIncomingSmsNotification(
            threadId = threadId,
            messageId = localId,
            address = cleanAddress,
            title = contactName ?: cleanAddress,
            body = body
        )
    }

    private suspend fun postIncomingSmsNotification(
        threadId: Long,
        messageId: Long,
        address: String,
        title: String,
        body: String
    ) {
        val settings = settingsRepository.getCurrentSettings()
        if (!settings.smsNotificationsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AndroidCompat.hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return
        }
        val channelId = AndroidCompat.ensureSmsNotificationChannel(
            context = context,
            title = "SMS Messages",
            channelVersion = settings.notificationChannelVersion,
            ringtoneUriString = settings.smsRingtoneUri,
            vibrate = settings.notificationVibrate
        )
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_THREAD_ID, threadId)
            putExtra(MainActivity.EXTRA_OPEN_ADDRESS, address)
            putExtra(MainActivity.EXTRA_OPEN_MESSAGE_ID, messageId)
        }
        val pi = PendingIntent.getActivity(
            context,
            threadId.toInt(),
            launchIntent,
            AndroidCompat.pendingIntentImmutableFlags()
        )
        val displayBody = if (settings.showNotificationPreviewText) {
            body
        } else {
            "New SMS message received"
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)

        if (!settings.showNotificationPreviewText) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        }
        if (settings.smsRingtoneUri == AndroidCompat.RINGTONE_SILENT_VALUE) {
            builder.setSilent(true)
        } else if (settings.smsRingtoneUri.isNotBlank()) {
            runCatching { Uri.parse(settings.smsRingtoneUri) }.getOrNull()?.let { uri ->
                builder.setSound(uri)
            }
        } else {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }
        if (!settings.notificationVibrate) {
            builder.setVibrate(longArrayOf(0L))
        } else {
            builder.setDefaults(Notification.DEFAULT_LIGHTS or Notification.DEFAULT_VIBRATE)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(threadId.toInt(), builder.build())
    }

    suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
        smsDao.markThreadRead(threadId)
        if (AndroidCompat.isDefaultSmsApp(context)) {
            runCatching {
                val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(threadId.toString())
                )
            }
        }
    }

    suspend fun deleteMessage(message: SmsMessage) = withContext(Dispatchers.IO) {
        smsDao.deleteMessageById(message.id)
        if (AndroidCompat.isDefaultSmsApp(context) && message.systemSmsId != null) {
            runCatching {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(message.systemSmsId.toString())
                )
            }
        }
    }

    suspend fun deleteThread(threadId: Long) = withContext(Dispatchers.IO) {
        smsDao.unpinThread(threadId)
        smsDao.deleteThread(threadId)
        if (AndroidCompat.isDefaultSmsApp(context)) {
            runCatching {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString())
                )
            }
        }
    }

    suspend fun setMessagePinned(messageId: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        smsDao.setMessagePinned(
            id = messageId,
            pinned = pinned,
            pinnedAt = if (pinned) System.currentTimeMillis() else 0L
        )
    }

    suspend fun setThreadPinned(threadId: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        if (pinned) {
            smsDao.pinThread(PinnedThreadEntity(threadId = threadId, pinnedAt = System.currentTimeMillis()))
        } else {
            smsDao.unpinThread(threadId)
        }
    }
}
