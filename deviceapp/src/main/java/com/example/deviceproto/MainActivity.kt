package com.example.deviceproto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity: AppCompatActivity() {
  private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    ensurePerms()
    findViewById<TextView>(R.id.statusView).text =
      "Ready. Responds to SMS: SEND DATA 9213 from allowed contacts."
  }

  private fun ensurePerms(){
    val needed = listOf(
      Manifest.permission.RECEIVE_SMS,
      Manifest.permission.READ_SMS,
      Manifest.permission.SEND_SMS,
      Manifest.permission.READ_CONTACTS,
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    if (needed.isNotEmpty()) perms.launch(needed.toTypedArray())
  }
}
