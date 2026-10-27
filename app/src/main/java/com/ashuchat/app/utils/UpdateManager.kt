package com.ashuchat.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersionName: String,
    val latestVersionCode: Long = 0L,
    val releaseNotes: String = "",
    val downloadUrl: String = ""
)

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_REPO = "itsmeyuvraj/AshuChat"
        private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val FIREBASE_UPDATE_URL = "https://ashuchat-51b3e-default-rtdb.asia-southeast1.firebasedatabase.app/app_update.json"
    }

    val currentVersionName: String
        get() = try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

    val currentVersionCode: Long
        get() = try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        var latestFoundVersionName = currentVersionName
        var latestFoundVersionCode = currentVersionCode
        var latestFoundReleaseNotes = ""

        // Step 1: Check Firebase Realtime DB endpoint first (instant, custom controllable)
        try {
            val fbReq = Request.Builder()
                .url(FIREBASE_UPDATE_URL)
                .build()
            val fbResp = client.newCall(fbReq).execute()
            if (fbResp.isSuccessful) {
                val jsonStr = fbResp.body?.string()
                fbResp.close()
                if (!jsonStr.isNullOrBlank() && jsonStr != "null") {
                    val parsed = JsonParser.parseString(jsonStr)
                    if (parsed.isJsonObject) {
                        val root = parsed.asJsonObject
                        val remoteVersionCode = root.get("versionCode")?.asLong ?: 0L
                        val remoteVersionName = root.get("versionName")?.asString ?: ""
                        val downloadUrl = root.get("downloadUrl")?.asString ?: ""
                        val releaseNotes = root.get("releaseNotes")?.asString ?: ""

                        if (releaseNotes.isNotBlank()) {
                            latestFoundReleaseNotes = releaseNotes
                            if (remoteVersionName.isNotBlank()) latestFoundVersionName = remoteVersionName
                            if (remoteVersionCode > 0) latestFoundVersionCode = remoteVersionCode
                        }

                        val isNewer = if (remoteVersionCode > 0 && currentVersionCode > 0) {
                            remoteVersionCode > currentVersionCode
                        } else if (remoteVersionName.isNotBlank() && currentVersionName.isNotBlank()) {
                            isNewerVersion(remoteVersionName, currentVersionName)
                        } else {
                            false
                        }

                        if (isNewer && downloadUrl.isNotBlank()) {
                            Log.d(TAG, "Update found via Firebase: $remoteVersionName ($remoteVersionCode)")
                            return@withContext AppUpdateInfo(
                                isUpdateAvailable = true,
                                latestVersionName = remoteVersionName.ifBlank { "v$remoteVersionCode" },
                                latestVersionCode = remoteVersionCode,
                                releaseNotes = releaseNotes.ifBlank { "Bug fixes and improvements" },
                                downloadUrl = downloadUrl
                            )
                        }
                    }
                }
            } else {
                fbResp.close()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Firebase update check skipped: ${e.localizedMessage}")
        }

        // Step 2: Check GitHub Releases API
        try {
            val ghReq = Request.Builder()
                .url(GITHUB_RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AshuChat-Android")
                .build()
            val ghResp = client.newCall(ghReq).execute()
            if (ghResp.isSuccessful) {
                val jsonStr = ghResp.body?.string()
                ghResp.close()
                if (!jsonStr.isNullOrBlank()) {
                    val parsed = JsonParser.parseString(jsonStr)
                    if (parsed.isJsonObject) {
                        val root = parsed.asJsonObject
                        val tagName = root.get("tag_name")?.asString ?: ""
                        val body = root.get("body")?.asString ?: ""

                        if (body.isNotBlank() && latestFoundReleaseNotes.isBlank()) {
                            latestFoundReleaseNotes = body
                            if (tagName.isNotBlank()) latestFoundVersionName = tagName
                        }

                        var apkDownloadUrl = ""
                        val assets = root.getAsJsonArray("assets")
                        if (assets != null) {
                            for (element in assets) {
                                val asset = element.asJsonObject
                                val name = asset.get("name")?.asString ?: ""
                                val download = asset.get("browser_download_url")?.asString ?: ""
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    apkDownloadUrl = download
                                    break
                                }
                            }
                        }

                        if (apkDownloadUrl.isBlank() && tagName.isNotBlank()) {
                            apkDownloadUrl = "https://github.com/$GITHUB_REPO/releases/download/$tagName/AshuChat.apk"
                        }

                        if (tagName.isNotBlank() && isNewerVersion(tagName, currentVersionName)) {
                            Log.d(TAG, "Update found via GitHub Releases: $tagName")
                            return@withContext AppUpdateInfo(
                                isUpdateAvailable = true,
                                latestVersionName = tagName,
                                latestVersionCode = currentVersionCode + 1,
                                releaseNotes = body.ifBlank { "Bug fixes and performance improvements" },
                                downloadUrl = apkDownloadUrl
                            )
                        }
                    }
                }
            } else {
                ghResp.close()
            }
        } catch (e: Exception) {
            Log.d(TAG, "GitHub update check skipped: ${e.localizedMessage}")
        }

        // No newer update available, but retain release notes of current/latest version
        return@withContext AppUpdateInfo(
            isUpdateAvailable = false,
            latestVersionName = latestFoundVersionName.ifBlank { currentVersionName },
            latestVersionCode = if (latestFoundVersionCode > 0) latestFoundVersionCode else currentVersionCode,
            releaseNotes = latestFoundReleaseNotes,
            downloadUrl = ""
        )
    }

    fun getEmbeddedReleaseNotes(): String {
        return try {
            context.assets.open("release_notes.txt").bufferedReader().use { it.readText() }.trim()
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun fetchReleaseNotesForVersion(versionName: String): String? = withContext(Dispatchers.IO) {
        val clean = versionName.trimStart('v', 'V').trim()
        val embeddedNotes = if (clean.equals(currentVersionName.trimStart('v', 'V').trim(), ignoreCase = true)) {
            getEmbeddedReleaseNotes()
        } else {
            ""
        }

        if (clean.isBlank()) return@withContext embeddedNotes.ifBlank { null }

        // 1. Try Firebase if versionName matches
        try {
            val fbReq = Request.Builder().url(FIREBASE_UPDATE_URL).build()
            val fbResp = client.newCall(fbReq).execute()
            if (fbResp.isSuccessful) {
                val jsonStr = fbResp.body?.string()
                fbResp.close()
                if (!jsonStr.isNullOrBlank() && jsonStr != "null") {
                    val root = JsonParser.parseString(jsonStr).asJsonObject
                    val vName = root.get("versionName")?.asString?.trimStart('v', 'V')?.trim() ?: ""
                    val notes = root.get("releaseNotes")?.asString ?: ""
                    if (vName.equals(clean, ignoreCase = true) && notes.isNotBlank()) {
                        return@withContext notes
                    }
                }
            } else {
                fbResp.close()
            }
        } catch (_: Exception) {}

        // 2. Query GitHub release by specific tag (vX.Y.Z)
        try {
            val tagUrl = "https://api.github.com/repos/$GITHUB_REPO/releases/tags/v$clean"
            val ghReq = Request.Builder()
                .url(tagUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AshuChat-Android")
                .build()
            val ghResp = client.newCall(ghReq).execute()
            if (ghResp.isSuccessful) {
                val jsonStr = ghResp.body?.string()
                ghResp.close()
                if (!jsonStr.isNullOrBlank()) {
                    val root = JsonParser.parseString(jsonStr).asJsonObject
                    val body = root.get("body")?.asString ?: ""
                    if (body.isNotBlank()) {
                        return@withContext body
                    }
                }
            } else {
                ghResp.close()
            }
        } catch (_: Exception) {}

        // 3. Fallback: Query GitHub latest release if it matches the requested version
        try {
            val ghReq = Request.Builder()
                .url(GITHUB_RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AshuChat-Android")
                .build()
            val ghResp = client.newCall(ghReq).execute()
            if (ghResp.isSuccessful) {
                val jsonStr = ghResp.body?.string()
                ghResp.close()
                if (!jsonStr.isNullOrBlank()) {
                    val root = JsonParser.parseString(jsonStr).asJsonObject
                    val tagName = root.get("tag_name")?.asString?.trimStart('v', 'V')?.trim() ?: ""
                    val body = root.get("body")?.asString ?: ""
                    if (body.isNotBlank() && (tagName.isBlank() || tagName.equals(clean, ignoreCase = true))) {
                        return@withContext body
                    }
                }
            } else {
                ghResp.close()
            }
        } catch (_: Exception) {}

        embeddedNotes.ifBlank { null }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "AshuChat-Android")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return@withContext null
            }

            val body = resp.body ?: return@withContext null
            val contentLength = body.contentLength()

            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val apkFile = File(updatesDir, "AshuChat_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead: Long = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }
            resp.close()
            return@withContext apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update APK: ${e.localizedMessage}", e)
            return@withContext null
        }
    }

    fun promptInstall(apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+ verify package install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger APK installer: ${e.localizedMessage}", e)
        }
    }

    private fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = remoteVersion.trimStart('v', 'V').trim()
        val cleanCurrent = currentVersion.trimStart('v', 'V').trim()

        val remoteParts = cleanRemote.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
        val currentParts = cleanCurrent.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
