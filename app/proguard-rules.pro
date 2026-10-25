# WebRTC Proguard Rules
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Gson Proguard Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ashuchat.app.data.model.** { *; }

# Room Proguard Rules
-keep class * extends androidx.room.RoomDatabase
-keep class com.ashuchat.app.data.local.** { *; }
-dontwarn androidx.room.paging.**

# App Update Models
-keep class com.ashuchat.app.utils.AppUpdateInfo { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes *Annotation*

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# ML Kit Barcode & ZXing
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.zxing.** { *; }

# CameraX
-dontwarn androidx.camera.**
-keep class androidx.camera.core.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

