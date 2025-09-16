package com.example.wearproto

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Wear listener service:
 * - Receives "/request_vitals" from the phone
 * - Samples HR and (if exposed) SpO2 for ~3s
 * - Replies "/vitals_response" with "hr=<int>,spo2=<int| -1>,ts=<unix>"
 */
class WearMsgService : WearableListenerService(), SensorEventListener {

    private lateinit var sm: SensorManager
    @Volatile private var hr: Int = -1
    @Volatile private var spo2: Int = -1

    override fun onCreate() {
        super.onCreate()
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != REQUEST_PATH) return

        // If BODY_SENSORS not granted, return sentinel values quickly
        val hasSensorsPerm = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasSensorsPerm) {
            sendResponse(event.sourceNodeId, -1, -1)
            return
        }

        // Reset last readings
        hr = -1
        spo2 = -1

        // Register sensors if present
        val hrSensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val spo2Sensor = findSpo2Sensor()
        if (hrSensor != null) sm.registerListener(this, hrSensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (spo2Sensor != null) sm.registerListener(this, spo2Sensor, SensorManager.SENSOR_DELAY_NORMAL)

        // Wait for first samples or timeout (~3s), then respond
        Thread {
            try {
                val deadline = System.currentTimeMillis() + 3_000
                while (System.currentTimeMillis() < deadline) {
                    // If HR arrived and either SpO2 arrived or there's no SpO2 sensor, we can stop early
                    if (hr > 0 && (spo2 > 0 || spo2Sensor == null)) break
                    Thread.sleep(100)
                }
            } catch (_: Throwable) {
            } finally {
                sm.unregisterListener(this)
                sendResponse(event.sourceNodeId, hr, spo2)
            }
        }.start()
    }

    override fun onSensorChanged(e: SensorEvent?) {
        if (e == null) return
        when (e.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val v = e.values.firstOrNull() ?: return
                hr = v.toInt().coerceAtLeast(0)
            }
            else -> {
                // Heuristic: look for vendor SpO2 sensors by name
                val name = (e.sensor.name ?: "").lowercase()
                if (name.contains("spo2") || name.contains("oxygen") || name.contains("blood oxygen")) {
                    val v = e.values.firstOrNull() ?: return
                    spo2 = v.toInt().coerceIn(70, 100) // clamp to plausible range
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }

    private fun findSpo2Sensor(): Sensor? {
        val all = sm.getSensorList(Sensor.TYPE_ALL) ?: return null
        return all.firstOrNull { s ->
            val n = (s.name ?: "").lowercase()
            n.contains("spo2") || n.contains("oxygen") || n.contains("blood oxygen")
        }
    }

    private fun sendResponse(nodeId: String, hrVal: Int, spo2Val: Int) {
        val ts = System.currentTimeMillis() / 1000
        val payload = "hr=$hrVal,spo2=$spo2Val,ts=$ts"
        Wearable.getMessageClient(this)
            .sendMessage(nodeId, RESPONSE_PATH, payload.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val REQUEST_PATH = "/request_vitals"
        private const val RESPONSE_PATH = "/vitals_response"
    }
}
