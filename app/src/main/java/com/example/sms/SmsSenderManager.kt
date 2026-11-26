package com.example.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.compat.AndroidCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsSenderManager(private val context: Context) {

    companion object {
        const val ACTION_SMS_SENT = "com.example.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.ACTION_SMS_DELIVERED"
        const val EXTRA_LOCAL_MESSAGE_ID = "extra_local_message_id"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"
        private const val TAG = "SmsSenderManager"
    }

    suspend fun sendSms(
        localMessageId: Long,
        destinationAddress: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val smsManager = AndroidCompat.getSmsManager(context)
            val parts = smsManager.divideMessage(body)
            val flags = AndroidCompat.pendingIntentImmutableFlags()

            if (parts.size <= 1) {
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    (localMessageId * 1000L).toInt(),
                    Intent(ACTION_SMS_SENT).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_LOCAL_MESSAGE_ID, localMessageId)
                        putExtra(EXTRA_PART_INDEX, 0)
                        putExtra(EXTRA_TOTAL_PARTS, 1)
                    },
                    flags
                )
                val deliveredIntent = PendingIntent.getBroadcast(
                    context,
                    (localMessageId * 1000L + 500L).toInt(),
                    Intent(ACTION_SMS_DELIVERED).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_LOCAL_MESSAGE_ID, localMessageId)
                        putExtra(EXTRA_PART_INDEX, 0)
                        putExtra(EXTRA_TOTAL_PARTS, 1)
                    },
                    flags
                )
                smsManager.sendTextMessage(
                    destinationAddress,
                    null,
                    body,
                    sentIntent,
                    deliveredIntent
                )
            } else {
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                val deliveryIntents = ArrayList<PendingIntent>(parts.size)
                for (index in parts.indices) {
                    val sentPi = PendingIntent.getBroadcast(
                        context,
                        (localMessageId * 1000L + index).toInt(),
                        Intent(ACTION_SMS_SENT).apply {
                            setPackage(context.packageName)
                            putExtra(EXTRA_LOCAL_MESSAGE_ID, localMessageId)
                            putExtra(EXTRA_PART_INDEX, index)
                            putExtra(EXTRA_TOTAL_PARTS, parts.size)
                        },
                        flags
                    )
                    val deliveredPi = PendingIntent.getBroadcast(
                        context,
                        (localMessageId * 1000L + 500L + index).toInt(),
                        Intent(ACTION_SMS_DELIVERED).apply {
                            setPackage(context.packageName)
                            putExtra(EXTRA_LOCAL_MESSAGE_ID, localMessageId)
                            putExtra(EXTRA_PART_INDEX, index)
                            putExtra(EXTRA_TOTAL_PARTS, parts.size)
                        },
                        flags
                    )
                    sentIntents.add(sentPi)
                    deliveryIntents.add(deliveredPi)
                }
                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            }
        }.onFailure { err ->
            Log.e(TAG, "Failed to dispatch SMS", err)
        }
    }
}
