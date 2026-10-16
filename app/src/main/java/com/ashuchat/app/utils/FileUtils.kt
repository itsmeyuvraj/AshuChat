package com.ashuchat.app.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

object FileUtils {

    fun getFileName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return sanitizeFileName(name ?: "attachment_${System.currentTimeMillis()}")
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size: Long = 0L
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        }
        if (size == 0L) {
            uri.path?.let {
                val f = File(it)
                if (f.exists()) size = f.length()
            }
        }
        return size
    }

    fun getMimeType(context: Context, uri: Uri): String {
        var mimeType: String? = null
        if (uri.scheme == "content") {
            mimeType = context.contentResolver.getType(uri)
        } else {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (extension != null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
            }
        }
        return mimeType ?: "application/octet-stream"
    }

    fun sanitizeFileName(fileName: String): String {
        return fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024f * 1024f * 1024f))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }

    fun isImageMimeType(mimeType: String?): Boolean {
        return mimeType?.startsWith("image/") == true
    }

    fun getAttachmentsDir(context: Context): File {
        val dir = File(context.filesDir, "attachments")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun copyUriToAppStorage(context: Context, uri: Uri, preferredName: String? = null): String {
        val fileName = sanitizeFileName(preferredName ?: getFileName(context, uri))
        val destFile = File(getAttachmentsDir(context), "${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read URI: $uri")
        return destFile.absolutePath
    }

    fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    fun clearAllLocalFiles(context: Context) {
        try {
            // Delete all stored attachments
            getAttachmentsDir(context).listFiles()?.forEach { it.deleteRecursively() }
            // Delete cache files (temp camera captures, chunks)
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            // Delete external files if any
            context.getExternalFilesDir(null)?.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {}
    }
}
