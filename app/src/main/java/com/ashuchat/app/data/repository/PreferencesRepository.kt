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

        // Default Serverless Public Relay URL (100% free, zero setup, global public serverless broker)
        const val DEFAULT_SIGNALING_URL = "https://ntfy.sh"
        const val PUBLIC_RELAY_URL = "https://ntfy.sh"
        const val LOCAL_RELAY_URL = "ws://10.0.2.2:8080"
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
        get() {
            val saved = prefs.getString(KEY_SIGNALING_URL, null)
            if (saved == null || 
                saved.isBlank() || 
                saved.contains("ashuchat-relay.onrender.com") || 
                saved.contains("ashuchat-couples-default-rtdb")) {
                prefs.edit().putString(KEY_SIGNALING_URL, DEFAULT_SIGNALING_URL).apply()
                return DEFAULT_SIGNALING_URL
            }
            return saved
        }
        set(value) = prefs.edit().putString(KEY_SIGNALING_URL, value.trim()).apply()

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
