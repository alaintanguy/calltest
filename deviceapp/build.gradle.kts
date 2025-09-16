plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "com.example.deviceproto"
  compileSdk = 34
  defaultConfig {
    applicationId = "com.example.deviceproto"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}
dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("com.google.android.gms:play-services-location:21.2.0")
  implementation("com.google.android.gms:play-services-wearable:18.1.0")
  implementation("com.google.android.gms:play-services-tasks:18.1.0")
}
