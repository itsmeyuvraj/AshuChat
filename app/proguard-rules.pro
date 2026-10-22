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
