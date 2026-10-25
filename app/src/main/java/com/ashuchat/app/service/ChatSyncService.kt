package com.ashuchat.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ashuchat.app.MainActivity
import com.ashuchat.app.utils.NotificationHelper
import com.ashuchat.app.webrtc.ChatConnectionManager

/**
 * P3: Background Foreground Service
 * Keeps the P2P connection, signaling listeners, and incoming call listeners alive
 * when the app is in the background, swiped away, or the screen is locked.
 */
class ChatSyncService : Service() {

    companion object {
        private const val TAG = "ChatSyncService"
        private const val NOTIFICATION_ID = 2001

        fun startService(context: Context) {
            try {
                val intent = Intent(context, ChatSyncService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed starting ChatSyncService: ${e.localizedMessage}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, ChatSyncService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.initNotificationChannels(this)

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()

        // Ensure persistent connection coordinator is active
        ChatConnectionManager.init(applicationContext)
        ChatConnectionManager.start()
        Log.d(TAG, "ChatSyncService started and connected in foreground mode")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acquireWakeLock()
        ChatConnectionManager.init(applicationContext)
        ChatConnectionManager.start()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed by user. Re-scheduling background sync service...")
        try {
            val restartIntent = Intent(applicationContext, ChatSyncService::class.java).also {
                it.setPackage(packageName)
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext,
                101,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000L,
                restartPendingIntent
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed re-scheduling after task removal: ${e.localizedMessage}")
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AshuChat Active")
            .setContentText("Connected & listening for calls & messages")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AshuChat:SyncWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 1000L) // 10-second transient acquisition for service startup
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }
}
