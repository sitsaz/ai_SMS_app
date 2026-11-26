package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.AiSmsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val grouped = LinkedHashMap<String, Pair<StringBuilder, Long>>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "Unknown"
            val entry = grouped.getOrPut(sender) {
                StringBuilder() to sms.timestampMillis
            }
            entry.first.append(sms.displayMessageBody ?: sms.messageBody.orEmpty())
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as AiSmsApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((sender, content) in grouped) {
                    app.container.smsRepository.handleIncomingSms(
                        address = sender,
                        body = content.first.toString(),
                        timestamp = content.second
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Required manifest receiver for Default SMS app role eligibility.
    }
}
