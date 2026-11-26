package com.example.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.example.AiSmsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val recipient = intent.data?.schemeSpecificPart?.trim().orEmpty()
            val message = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (recipient.isNotEmpty() && message.isNotEmpty()) {
                val app = applicationContext as AiSmsApplication
                CoroutineScope(Dispatchers.IO).launch {
                    app.container.smsRepository.sendSmsMessage(recipient, message)
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
