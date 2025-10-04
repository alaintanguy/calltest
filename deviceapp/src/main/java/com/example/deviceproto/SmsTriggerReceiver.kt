package com.example.deviceproto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log

class SmsTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_RECEIVED) return

        try {
            val extras: Bundle = intent.extras ?: return
            val pdus = extras.get("pdus") as? Array<*> ?: return
            val format = extras.getString("format")

            for (p in pdus) {
                val msg = if (Build.VERSION.SDK_INT >= 23) {
                    SmsMessage.createFromPdu(p as ByteArray, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(p as ByteArray)
                }

                val from = msg.originatingAddress ?: continue
                val body = msg.messageBody ?: continue
                // 🔧 FALLBACK: reply to ANY SMS (temporary diagnostic)
sendReplySafely(context, from, "AUTO: got your message")
continue  // or 'return' if you only process the first PDU

                Log.i(TAG, "SMS from=$from body=\"$body\"")

                // 1) Only process if sender is a starred (favorite) contact
                val isFav = ContactsUtils.isNumberFavorited(context, from)
                if (!isFav) {
                    Log.i(TAG, "Ignoring non-favorite sender: $from")
                    continue
                }

                // 2) Match command
                if (CMD_REGEX.matches(body)) {
                    val reply = "OK: DATA 9213 RECEIVED"
                    sendReplySafely(context, from, reply)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive crash", t)
        }
    }

    private fun sendReplySafely(context: Context, to: String, text: String) {
        try {
            val sm = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            sm.sendTextMessage(to, null, text, null, null)
            Log.i(TAG, "Reply sent to $to")
        } catch (se: SecurityException) {
            Log.e(TAG, "SEND_SMS not granted/restricted", se)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to send reply", t)
        }
    }

    companion object {
        private const val TAG = "SmsTriggerReceiver"
        private const val ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED"
        // Accepts case/spacing variants, e.g., " send   data 9213 "
        val CMD_REGEX = Regex("""^\s*send\s+data\s+9213\s*$""", RegexOption.IGNORE_CASE)
    }
}

