package com.example.wearproto

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.Charset

class WearMsgService: WearableListenerService(), SensorEventListener {
  private var sm: SensorManager? = null
  @Volatile private var hr: Int = -1
  @Volatile private var spo2: Int = -1

  override fun onCreate() {
    super.onCreate()
    sm = getSystemService(SENSOR_SERVICE) as SensorManager
  }

  override fun onMessageReceived(event: MessageEvent) {
    if (event.path != "/request_vitals") return
    val hrSensor = sm?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    val spo2Sensor = findSpo2Sensor()
    if (hrSensor != null) sm?.registerListener(this, hrSensor, SensorManager.SENSOR_DELAY_NORMAL)
    if (spo2Sensor != null) sm?.registerListener(this, spo2Sensor, SensorManager.SENSOR_DELAY_NORMAL)

    Thread {
      try { Thread.sleep(3000) } catch (_: Throwable) {}
      sm?.unregisterListener(this)
      val ts = System.currentTimeMillis()/1000
      val payload = "hr="+hr+",spo2="+spo2+",ts="+ts
      Wearable.getMessageClient(this)
        .sendMessage(event.sourceNodeId, "/vitals_response", payload.toByteArray(Charset.forName("UTF-8")))
    }.start()
  }

  private fun findSpo2Sensor(): Sensor? {
    val list = sm?.getSensorList(Sensor.TYPE_ALL) ?: return null
    return list.firstOrNull { s ->
      val n = (s.name ?: "").lowercase()
      n.contains("spo2") || n.contains("oxygen")
    }
  }

  override fun onSensorChanged(e: SensorEvent?) {
    if (e == null) return
    when (e.sensor.type) {
      Sensor.TYPE_HEART_RATE -> { e.values.firstOrNull()?.toInt()?.let { hr = it } }
      else -> {
        val n = e.sensor.name?.lowercase() ?: ""
        if (n.contains("spo2") or n.contains("oxygen"))
          e.values.firstOrNull()?.toInt()?.let { spo2 = it.coerceIn(70, 100) }
      }
    }
  }
  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
