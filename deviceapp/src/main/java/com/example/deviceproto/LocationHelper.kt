package com.example.deviceproto

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LocationHelper {
  data class Fix(val lat: Double, val lon: Double)

  @SuppressLint("MissingPermission")
  fun getCurrentOrLast(context: Context, timeoutSec: Long = 5): Fix {
    val fused: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    var out: Fix? = null
    val latch = CountDownLatch(1)

    val task = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
    task.addOnSuccessListener { loc ->
      if (loc != null) { out = Fix(loc.latitude, loc.longitude); latch.countDown() }
    }.addOnFailureListener { latch.countDown() }
    latch.await(timeoutSec, TimeUnit.SECONDS)

    if (out == null) {
      val lastLatch = CountDownLatch(1)
      fused.lastLocation.addOnSuccessListener { loc ->
        if (loc != null) out = Fix(loc.latitude, loc.longitude)
        lastLatch.countDown()
      }.addOnFailureListener { lastLatch.countDown() }
      lastLatch.await(2, TimeUnit.SECONDS)
    }
    return out ?: Fix(0.0, 0.0)
  }
}
