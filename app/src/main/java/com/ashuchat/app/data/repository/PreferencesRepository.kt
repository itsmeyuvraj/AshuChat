package com.ashuchat.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ashuchat_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MY_NAME = "my_name"
        private const val KEY_PARTNER_NAME = "partner_name"
        private const val KEY_ROOM_ID = "room_id"
        private const val KEY_SIGNALING_URL = "signaling_url"
        private const val KEY_CLIENT_ID = "client_id"

        // Default Serverless Firebase Realtime Database URL (Zero hosting, Google serverless cloud)
        const val DEFAULT_SIGNALING_URL = "https://ashuchat-couples-default-rtdb.firebaseio.com"
        const val PUBLIC_RELAY_URL = "wss://ashuchat-relay.onrender.com"
    }

    var myName: String
        get() = prefs.getString(KEY_MY_NAME, "Yuvraj") ?: "Yuvraj"
        set(value) = prefs.edit().putString(KEY_MY_NAME, value).apply()

    var partnerName: String
        get() = prefs.getString(KEY_PARTNER_NAME, "Ashu") ?: "Ashu"
        set(value) = prefs.edit().putString(KEY_PARTNER_NAME, value).apply()

    var roomId: String
        get() = prefs.getString(KEY_ROOM_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ROOM_ID, value).apply()

    var signalingUrl: String
        get() = prefs.getString(KEY_SIGNALING_URL, DEFAULT_SIGNALING_URL) ?: DEFAULT_SIGNALING_URL
        set(value) = prefs.edit().putString(KEY_SIGNALING_URL, value).apply()

    val clientId: String
        get() {
            var id = prefs.getString(KEY_CLIENT_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_CLIENT_ID, id).apply()
            }
            return id
        }

    fun clearRoom() {
        prefs.edit().remove(KEY_ROOM_ID).apply()
    }
}
