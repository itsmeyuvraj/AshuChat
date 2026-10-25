package com.ashuchat.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.ashuchat.app.MainActivity

object NotificationHelper {

    const val CHANNEL_MESSAGES = "ashuchat_messages"
    const val CHANNEL_CALLS = "ashuchat_calls"
    const val CHANNEL_SYNC = "ashuchat_background_sync"

    const val EXTRA_OPEN_ROOM_ID = "EXTRA_OPEN_ROOM_ID"

    @Volatile
    private var channelsInitialized = false

    fun initNotificationChannels(context: Context) {
        if (channelsInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Messages Channel
            val msgChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "AshuChat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Private P2P chat messages and alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 220, 100, 220)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(msgChannel)

            // 2. Incoming Calls Channel
            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "AshuChat Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            manager.createNotificationChannel(callChannel)

            // 3. Background Sync Service Channel
            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                "AshuChat Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains private chat connection in the background"
                setShowBadge(false)
            }
            manager.createNotificationChannel(syncChannel)
            channelsInitialized = true
        }
    }

    @Deprecated("Use initNotificationChannels instead", ReplaceWith("initNotificationChannels(context)"))
    fun initNotificationChannel(context: Context) = initNotificationChannels(context)

    fun showMessageNotification(
        context: Context,
        senderName: String,
        messageText: String,
        roomId: String? = null
    ) {
        initNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!roomId.isNullOrBlank()) {
                putExtra(EXTRA_OPEN_ROOM_ID, roomId)
            }
        }
        val requestCode = if (!roomId.isNullOrBlank()) roomId.hashCode() else 100
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notificationId = (System.currentTimeMillis() % 10000).toInt() + 1000
            manager?.notify(notificationId, notification)
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    fun vibratePing(context: Context) {
        try {
            // Modern double-pulse haptic alert
            val pattern = longArrayOf(0, 120, 80, 120)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {}
    }

    @Deprecated("Use vibratePing instead", ReplaceWith("vibratePing(context)"))
    fun vibrateLovePing(context: Context) = vibratePing(context)
}
