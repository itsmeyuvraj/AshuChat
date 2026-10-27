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
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_FLAG_SECURE_ENABLED = "flag_secure_enabled"
        private const val KEY_DISAPPEARING_DURATION = "disappearing_duration"
        private const val KEY_LAST_RUN_VERSION_CODE = "last_run_version_code"
        private const val KEY_LAST_RUN_VERSION_NAME = "last_run_version_name"
        private const val KEY_LAST_APP_VERSION_CODE = "last_app_version_code"
        private const val KEY_LAST_APP_VERSION_NAME = "last_app_version_name"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_CURRENT_RELEASE_NOTES = "current_release_notes"
        private const val KEY_CURRENT_RELEASE_NOTES_VERSION = "current_release_notes_version"
        private const val KEY_PENDING_UPDATE_NOTES = "pending_update_notes"
        private const val KEY_PENDING_UPDATE_VERSION = "pending_update_version"

        // Default Serverless Firebase Realtime Database URL (Singapore asia-southeast1)
        const val DEFAULT_SIGNALING_URL = "https://ashuchat-51b3e-default-rtdb.asia-southeast1.firebasedatabase.app"
        const val PUBLIC_RELAY_URL = "https://ashuchat-51b3e-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    var myName: String
        get() = prefs.getString(KEY_MY_NAME, "You") ?: "You"
        set(value) = prefs.edit().putString(KEY_MY_NAME, value.trim()).apply()

    var partnerName: String
        get() = prefs.getString(KEY_PARTNER_NAME, "Peer") ?: "Peer"
        set(value) = prefs.edit().putString(KEY_PARTNER_NAME, value.trim()).apply()

    var roomId: String
        get() = prefs.getString(KEY_ROOM_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ROOM_ID, value).apply()

    var signalingUrl: String
        get() {
            val saved = prefs.getString(KEY_SIGNALING_URL, null)
            if (saved == null || 
                saved.isBlank() || 
                saved.contains("ntfy.sh") ||
                saved.contains("ashuchat-relay.onrender.com") || 
                saved.contains("ashuchat-couples-default-rtdb")) {
                prefs.edit().putString(KEY_SIGNALING_URL, DEFAULT_SIGNALING_URL).apply()
                return DEFAULT_SIGNALING_URL
            }
            return saved
        }
        set(value) = prefs.edit().putString(KEY_SIGNALING_URL, value.trim()).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var isFlagSecureEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLAG_SECURE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FLAG_SECURE_ENABLED, value).apply()

    var disappearingDurationSeconds: Long
        get() = prefs.getLong(KEY_DISAPPEARING_DURATION, 0L)
        set(value) = prefs.edit().putLong(KEY_DISAPPEARING_DURATION, value).apply()

    var lastRunVersionCode: Long
        get() = prefs.getLong(KEY_LAST_RUN_VERSION_CODE, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_RUN_VERSION_CODE, value).apply()

    var lastRunVersionName: String
        get() = prefs.getString(KEY_LAST_RUN_VERSION_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_RUN_VERSION_NAME, value).apply()

    var lastAppVersionCode: Long
        get() = prefs.getLong(KEY_LAST_APP_VERSION_CODE, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_APP_VERSION_CODE, value).apply()

    var lastAppVersionName: String
        get() = prefs.getString(KEY_LAST_APP_VERSION_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_APP_VERSION_NAME, value).apply()

    var appTheme: String
        get() = prefs.getString(KEY_APP_THEME, "classic") ?: "classic"
        set(value) = prefs.edit().putString(KEY_APP_THEME, value).apply()

    var currentReleaseNotes: String
        get() = prefs.getString(KEY_CURRENT_RELEASE_NOTES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CURRENT_RELEASE_NOTES, value).apply()

    var currentReleaseNotesVersion: String
        get() = prefs.getString(KEY_CURRENT_RELEASE_NOTES_VERSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CURRENT_RELEASE_NOTES_VERSION, value).apply()

    fun saveCurrentReleaseNotes(version: String, notes: String) {
        val clean = version.trimStart('v', 'V').trim()
        prefs.edit()
            .putString(KEY_CURRENT_RELEASE_NOTES_VERSION, clean)
            .putString(KEY_CURRENT_RELEASE_NOTES, notes)
            .apply()
    }

    fun getCurrentReleaseNotesForVersion(targetVersion: String): String {
        val cleanTarget = targetVersion.trimStart('v', 'V').trim()
        val storedVersion = prefs.getString(KEY_CURRENT_RELEASE_NOTES_VERSION, "")?.trimStart('v', 'V')?.trim() ?: ""
        val notes = prefs.getString(KEY_CURRENT_RELEASE_NOTES, "") ?: ""
        return if (storedVersion.isNotBlank() && storedVersion.equals(cleanTarget, ignoreCase = true)) {
            notes
        } else {
            "" // Discard stale notes from an older or different version
        }
    }

    fun clearCurrentReleaseNotes() {
        prefs.edit()
            .remove(KEY_CURRENT_RELEASE_NOTES)
            .remove(KEY_CURRENT_RELEASE_NOTES_VERSION)
            .apply()
    }

    var pendingUpdateNotes: String
        get() = prefs.getString(KEY_PENDING_UPDATE_NOTES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PENDING_UPDATE_NOTES, value).apply()

    var pendingUpdateVersion: String
        get() = prefs.getString(KEY_PENDING_UPDATE_VERSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PENDING_UPDATE_VERSION, value).apply()

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
