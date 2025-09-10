package com.example.deviceproto

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class WearVitalsClient(private val context: Context): MessageClient.OnMessageReceivedListener {
  @Volatile private var lastPayload: String? = null
  private val msgClient = Wearable.getMessageClient(context)
  private val nodeClient = Wearable.getNodeClient(context)

  override fun onMessageReceived(event: MessageEvent) {
    if (event.path == "/vitals_response") {
      lastPayload = String(event.data, Charset.forName("UTF-8"))
      Log.i("WearVitalsClient", "Got vitals: $lastPayload")
    }
  }

  fun requestVitalsOnce(timeoutSec: Long = 5): String? {
    lastPayload = null
    msgClient.addListener(this)
    try {
      val nodes = Tasks.await(nodeClient.connectedNodes, 2, TimeUnit.SECONDS)
      if (nodes.isNullOrEmpty()) return null
      val target = nodes.first().id
      msgClient.sendMessage(target, "/request_vitals", ByteArray(0))
      val end = System.currentTimeMillis() + timeoutSec*1000
      while (System.currentTimeMillis() < end) {
        val p = lastPayload
        if (p != null) return p
        Thread.sleep(100)
      }
      return null
    } catch (_: Throwable) {
      return null
    } finally {
      msgClient.removeListener(this)
    }
  }
}
