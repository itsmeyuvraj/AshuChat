package com.ashuchat.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ashuchat.app.utils.CallNotificationHelper
import com.ashuchat.app.webrtc.ChatConnectionManager

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "CallActionReceiver received action: $action")

        when (action) {
            CallNotificationHelper.ACTION_DECLINE_CALL -> {
                CallNotificationHelper.dismissIncomingCallNotification(context)
                ChatConnectionManager.rejectCall()
            }
        }
    }
}
