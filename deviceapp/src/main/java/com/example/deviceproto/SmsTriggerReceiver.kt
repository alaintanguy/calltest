package com.example.deviceproto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.format.DateFormat
import android.util.Log
import java.util.Locale

/**
 * Receives incoming SMS, validates sender & PIN, queries the Wear OS watch for vitals,
 * grabs phone GPS, and replies by SMS.
 *
 * Spec:
 *  - Trigger phrase: "SEND DATA 9213"
 *  - Sender must be in Contacts
 *  - One request per 60s (rate limit)
 *  - Query watch up to 3 attempts (5s each). If none -> "NOT AVAILABLE date=<...> time=<...>"
 *  - Successful reply: "DATA ts=<unix> date=<YYYY-MM-DD> time=<HH:mm:ss> hr=<bpm> spo2=<pct or -1> lat=<...> lon=<...>"
 */
class SmsTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        // Use goAsync() so we can finish after background work
        val pending = goAsync()
        Thread {
            try {
                val prefs = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
                val nowMs = System.currentTimeMillis()

                // --- 0) Rate limit: one request per 60s ---
                val last = prefs.getLong("last_req_ts", 0L)
                if (nowMs - last < 60_000L) {
                    Log.i(TAG, "Rate-limited, ignoring (≤60s since last).")
                    pending.finish()
                    return@Thread
                }

                // --- 1) Parse incoming SMS ---
                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                var from: String? = null
                val sb = StringBuilder()
                for (m in msgs) {
                    if (from == null) from = m.originatingAddress
                    sb.append(m.messageBody ?: "")
                }
                val body = sb.toString().trim()
                if (from.isNullOrBlank()) {
                    Log.w(TAG, "No originating address.")
                    pending.finish()
                    return@Thread
                }

                // --- 2) Sender must be in Contacts ---
                if (!ContactsHelper.isInContacts(context, from!!)) {
                    Log.i(TAG, "Sender $from is not in contacts, ignoring.")
                    pending.finish()
                    return@Thread
                }

                // --- 3) Validate keyword + PIN (case-insensitive, extra whitespace tolerated) ---
                val normalized = body.uppercase(Locale.US).replace("\\s+".toRegex(), " ").trim()
                if (!normalized.contains("SEND DATA 9213")) {
                    Log.i(TAG, "Keyword/PIN mismatch. Body='$body'")
                    pending.finish()
                    return@Thread
                }

                // Passed checks → mark timestamp now to enforce rate limit even if later steps fail
                prefs.edit().putLong("last_req_ts", nowMs).apply()

                // --- 4) Grab phone location at reply time (5s current, then last-known fallback) ---
                val fix = LocationHelper.getCurrentOrLast(context, timeoutSec = 5)

                // --- 5) Query Wear OS watch vitals (up to 3 attempts, 5s each) ---
                val wearClient = WearVitalsClient(context)
                var payload: String? = null
                repeat(3) { attempt ->
                    payload = wearClient.requestVitalsOnce(timeoutSec = 5)
                    if (payload != null) {
                        Log.i(TAG, "Got watch vitals on attempt ${attempt + 1}: $payload")
                        return@repeat
                    }
                    Log.i(TAG, "No vitals yet (attempt ${attempt + 1})")
                }

                // --- 6) Build reply ---
                val date = DateFormat.format("yyyy-MM-dd", nowMs).toString()
                val time = DateFormat.format("HH:mm:ss", nowMs).toString()

                val response: String = if (payload != null) {
                    val lat = String.format(Locale.US, "%.6f", fix.lat)
                    val lon = String.format(Locale.US, "%.6f", fix.lon)
                    String.format(
                        Locale.US,
                        "DATA ts=%d date=%s time=%s %s lat=%s lon=%s",
                        nowMs / 1000, date, time, payload, lat, lon
                    )
                } else {
                    String.format(Locale.US, "NOT AVAILABLE date=%s time=%s", date, time)
                }

                // --- 7) Send reply SMS to sender ---
                sendSms(context, from!!, response)
                Log.i(TAG, "Sent reply to $from: $response")
            } catch (t: Throwable) {
                Log.e(TAG, "Error handling SMS: ${t.message}", t)
            } finally {
                pending.finish()
            }
        }.start()
    }

    // SmsManager helper (API 31+ service or legacy default)
    private fun getSmsManager(ctx: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun sendSms(ctx: Context, to: String, body: String) {
        val mgr = getSmsManager(ctx)
        // If long, split into multipart so it doesn't get truncated
        val parts = mgr.divideMessage(body)
        if (parts.size <= 1) {
            mgr.sendTextMessage(to, null, body, null, null)
        } else {
            mgr.sendMultipartTextMessage(to, null, parts, null, null)
        }
    }

    companion object {
        private const val TAG = "SmsTrigger"
    }
}
