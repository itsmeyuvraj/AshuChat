# WebRTC Proguard Rules
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Gson Proguard Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ashuchat.app.data.model.** { *; }

# Room Proguard Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
