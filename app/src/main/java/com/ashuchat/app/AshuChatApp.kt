package com.ashuchat.app

import android.app.Application
import com.ashuchat.app.utils.NotificationHelper

class AshuChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.initNotificationChannel(this)
    }
}
