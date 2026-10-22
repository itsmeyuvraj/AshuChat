package com.ashuchat.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ashuchat.app.data.repository.PreferencesRepository

object PermissionHelper {

    /**
     * Returns all runtime permissions required by AshuChat for the current device API level.
     */
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        return permissions.toTypedArray()
    }

    /**
     * Returns only the permissions that are currently NOT granted.
     */
    fun getMissingPermissions(context: Context): Array<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    /**
     * True if all required permissions are granted.
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getMissingPermissions(context).isEmpty()
    }

    /**
     * Checks specifically for Camera permission.
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks specifically for Microphone / Audio recording permission.
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Determines whether permissions should be automatically requested upon app launch.
     * True if:
     * 1. This is the first time the user opens the app (lastRunVersionCode == -1L).
     * 2. The app has been updated to a new build (currentVersionCode != lastRunVersionCode or version name changed).
     * 3. Any required permission is currently missing.
     */
    fun shouldCheckPermissionsOnStartup(context: Context, prefs: PreferencesRepository): Boolean {
        val currentVersionCode = getAppVersionCode(context)
        val currentVersionName = getAppVersionName(context)
        val lastRunCode = prefs.lastRunVersionCode
        val lastRunName = prefs.lastRunVersionName

        // First launch or after update to a new build
        val isFirstLaunchOrUpdate = lastRunCode == -1L ||
                lastRunCode != currentVersionCode ||
                lastRunName != currentVersionName

        return isFirstLaunchOrUpdate || getMissingPermissions(context).isNotEmpty()
    }

    /**
     * Marks that startup permissions have been evaluated for this app build version.
     */
    fun markStartupPermissionsChecked(context: Context, prefs: PreferencesRepository) {
        prefs.lastRunVersionCode = getAppVersionCode(context)
        prefs.lastRunVersionName = getAppVersionName(context)
    }

    fun getAppVersionCode(context: Context): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    fun getAppVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
