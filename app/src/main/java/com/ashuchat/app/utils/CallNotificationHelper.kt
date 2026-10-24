package com.ashuchat.app.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.ashuchat.app.MainActivity
import com.ashuchat.app.receiver.CallActionReceiver

object CallNotificationHelper {

    private const val TAG = "CallNotificationHelper"
    const val NOTIFICATION_ID = 2002

    const val ACTION_ANSWER_CALL = "com.ashuchat.app.ACTION_ANSWER_CALL"
    const val ACTION_DECLINE_CALL = "com.ashuchat.app.ACTION_DECLINE_CALL"
    const val ACTION_INCOMING_CALL = "com.ashuchat.app.ACTION_INCOMING_CALL"
    const val EXTRA_CALL_ROOM_ID = "EXTRA_CALL_ROOM_ID"
    const val EXTRA_CALL_CALLER_NAME = "EXTRA_CALL_CALLER_NAME"
    const val EXTRA_IS_VIDEO_CALL = "EXTRA_IS_VIDEO_CALL"

    private var activeRingtone: Ringtone? = null
    private var isRinging = false

    @SuppressLint("MissingPermission")
    @Synchronized
    fun showIncomingCallNotification(
        context: Context,
        callerName: String,
        roomId: String,
        isVideo: Boolean = false
    ) {
        NotificationHelper.initNotificationChannels(context)

        // 1. Decline Action (Broadcasts to CallActionReceiver without opening app)
        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_CALL_ROOM_ID, roomId)
            putExtra(EXTRA_CALL_CALLER_NAME, callerName)
            putExtra(EXTRA_IS_VIDEO_CALL, isVideo)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Answer Action (Launches MainActivity to explicitly answer call)
        val answerIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_ANSWER_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ROOM_ID, roomId)
            putExtra(EXTRA_CALL_CALLER_NAME, callerName)
            putExtra(EXTRA_IS_VIDEO_CALL, isVideo)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            202,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Fullscreen / Tap Intent for lockscreen ringing (displays incoming call UI without auto-answering)
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_INCOMING_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ROOM_ID, roomId)
            putExtra(EXTRA_CALL_CALLER_NAME, callerName)
            putExtra(EXTRA_IS_VIDEO_CALL, isVideo)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            203,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            callerPerson,
            declinePendingIntent,
            answerPendingIntent
        ).setIsVideo(isVideo)

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_CALLS)
            .setSmallIcon(if (isVideo) android.R.drawable.ic_menu_camera else android.R.drawable.ic_menu_call)
            .setContentTitle(if (isVideo) "Incoming Video Call" else "Incoming Voice Call")
            .setContentText(if (isVideo) "$callerName is video calling..." else "$callerName is calling...")
            .setStyle(callStyle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true) // Prevent duplicate sound from channel alert; activeRingtone manages looping audio
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                manager.notify(NOTIFICATION_ID, notification)
            }
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

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        // Do not play ringtone or vibrate if device is in silent mode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }

        // Only play ringtone sound if device is in normal mode
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
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
            } catch (e: Exception) {
                Log.w(TAG, "Error starting ringtone: ${e.localizedMessage}")
            }
        }

        // Vibrate in both normal and vibrate modes
        try {
            val vibrator = getVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0, 800, 800, 800, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error starting vibration: ${e.localizedMessage}")
        }
    }

    private fun stopRinging(context: Context) {
        isRinging = false
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (_: Exception) {}

        try {
            getVibrator(context)?.cancel()
        } catch (_: Exception) {}
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
