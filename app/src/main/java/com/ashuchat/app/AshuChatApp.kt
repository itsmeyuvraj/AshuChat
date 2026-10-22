package com.ashuchat.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.ashuchat.app.utils.NotificationHelper

class AshuChatApp : Application() {

    companion object {
        var isAppInForeground = false
            private set
        var activeChatRoom: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.initNotificationChannels(this)
        com.ashuchat.app.webrtc.ChatConnectionManager.init(this)
        com.ashuchat.app.service.ChatSyncService.startService(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumedActivities = 0

            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                isAppInForeground = resumedActivities > 0
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
                isAppInForeground = resumedActivities > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
