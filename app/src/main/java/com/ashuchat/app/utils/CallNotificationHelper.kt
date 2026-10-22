package com.ashuchat.app.utils

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ashuchat.app.MainActivity
import com.ashuchat.app.receiver.CallActionReceiver

object CallNotificationHelper {

    private const val TAG = "CallNotificationHelper"
    const val NOTIFICATION_ID = 2002

    const val ACTION_ANSWER_CALL = "com.ashuchat.app.ACTION_ANSWER_CALL"
    const val ACTION_DECLINE_CALL = "com.ashuchat.app.ACTION_DECLINE_CALL"
    const val EXTRA_CALL_ROOM_ID = "EXTRA_CALL_ROOM_ID"
    const val EXTRA_CALL_CALLER_NAME = "EXTRA_CALL_CALLER_NAME"

    private var activeRingtone: Ringtone? = null
    private var isRinging = false

    @Synchronized
    fun showIncomingCallNotification(
        context: Context,
        callerName: String,
        roomId: String
    ) {
        NotificationHelper.initNotificationChannels(context)

        // 1. Decline Action (Broadcasts to CallActionReceiver without opening app)
        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_CALL_ROOM_ID, roomId)
            putExtra(EXTRA_CALL_CALLER_NAME, callerName)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Answer Action (Launches MainActivity to answer call)
        val answerIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_ANSWER_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ROOM_ID, roomId)
            putExtra(EXTRA_CALL_CALLER_NAME, callerName)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            202,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Fullscreen Intent for lockscreen ringing
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_ANSWER_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ROOM_ID, roomId)
            putExtra(EXTRA_CALL_CALLER_NAME, callerName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            203,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Voice Call")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(answerPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Decline",
                declinePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_call,
                "Answer",
                answerPendingIntent
            )
            .build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed notifying incoming call: ${e.localizedMessage}")
        }

        startRinging(context)
    }

    @Synchronized
    fun dismissIncomingCallNotification(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        stopRinging(context)
    }

    private fun startRinging(context: Context) {
        if (isRinging) return
        isRinging = true

        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            activeRingtone = RingtoneManager.getRingtone(context.applicationContext, ringtoneUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }

            // Start looping vibration
            val pattern = longArrayOf(0, 800, 800, 800, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createWaveform(pattern, 1))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(pattern, 1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error starting ringtone: ${e.localizedMessage}")
        }
    }

    private fun stopRinging(context: Context) {
        isRinging = false
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.cancel()
            }
        } catch (_: Exception) {}
    }
}
