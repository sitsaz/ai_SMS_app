package com.example.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.DeliveryStatus
import com.example.data.local.SmsArchiveDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val localId = intent.getLongExtra(SmsSenderManager.EXTRA_LOCAL_MESSAGE_ID, -1L)
        if (localId <= 0L) return

        val action = intent.action ?: return
        val resultCode = resultCode
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = SmsArchiveDatabase.getInstance(context).smsDao()
                when (action) {
                    SmsSenderManager.ACTION_SMS_SENT -> {
                        val nextStatus = if (resultCode == Activity.RESULT_OK) {
                            DeliveryStatus.SENT
                        } else {
                            DeliveryStatus.FAILED
                        }
                        val current = dao.getById(localId)
                        if (current?.deliveryStatus != DeliveryStatus.DELIVERED.name) {
                            dao.updateDeliveryStatus(localId, nextStatus.name)
                        }
                    }
                    SmsSenderManager.ACTION_SMS_DELIVERED -> {
                        if (resultCode == Activity.RESULT_OK) {
                            dao.updateDeliveryStatus(localId, DeliveryStatus.DELIVERED.name)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
