package com.example.wearproto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity: AppCompatActivity() {
  private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(TextView(this).apply { text = "WearProto: grant Body Sensors, then close." })
    val need = mutableListOf<String>()
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
      need += Manifest.permission.BODY_SENSORS
    if (need.isNotEmpty()) perms.launch(need.toTypedArray())
  }
}
