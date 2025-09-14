package com.example.deviceproto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.format.DateFormat
import android.util.Log
import java.util.Locale

class SmsTriggerReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

    val pending = goAsync()

    Thread {
      try {
        val prefs = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_req_ts", 0L)
        if (now - last < 60_000L) { pending.finish(); return@Thread } // 1/min

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        var from: String? = null
        var body = ""
        for (m in msgs) {
          from = m.originatingAddress
          body += m.messageBody ?: ""
        }
        if (from == null) { pending.finish(); return@Thread }

        // 1) must be in contacts
        if (!ContactsHelper.isInContacts(context, from!!)) { pending.finish(); return@Thread }

        // 2) keyword + PIN
        if (!body.trim().uppercase().contains("SEND DATA 9213")) { pending.finish(); return@Thread }

        prefs.edit().putLong("last_req_ts", now).apply()

        // 3) phone location at send time (nullable-safe)
        val fix = LocationHelper.getCurrentOrLast(context, 5) // assume this may be null

        // 4) ask watch (3 tries)
        val client = WearVitalsClient(context)
        var payload: String? = null
        for (i in 0 until 3) {
          payload = client.requestVitalsOnce(5)
          if (payload != null) break
        }

        // 5) reply text
        val date = DateFormat.format("yyyy-MM-dd", now).toString()
        val time = DateFormat.format("HH:mm:ss", now).toString()

        val resp: String = if (payload != null) {
          val lat = fix?.let { String.format(Locale.US, "%.6f", it.lat) }
          val lon = fix?.let { String.format(Locale.US, "%.6f", it.lon) }
          String.format(
            Locale.US,
            "DATA ts=%d date=%s time=%s %s%s",
            now / 1000, date, time, payload,
            if (lat != null && lon != null) " lat=$lat lon=$lon" else ""
          )
        } else {
          String.format(Locale.US, "NOT AVAILABLE date=%s time=%s", date, time)
        }

        // 6) send SMS
        val sms = context.getSystemService(SmsManager::class.java)
        sms.sendTextMessage(from, null, resp, null, null)

        Log.i("SmsTrigger", "Sent: $resp")
      } catch (e: Exception) {
        Log.e("SmsTrigger", "Error in SmsTriggerReceiver", e)
      } finally {
        pending.finish()
      }
    }.start()
  }
}
