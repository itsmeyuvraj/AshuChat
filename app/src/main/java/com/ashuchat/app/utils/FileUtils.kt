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
            try {
                mimeType = context.contentResolver.getType(uri)
            } catch (_: Exception) {}
        }
        if (mimeType.isNullOrBlank() || mimeType == "application/octet-stream") {
            val name = getFileName(context, uri)
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext.isNotBlank()) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
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
        return mimeType?.startsWith("image/", ignoreCase = true) == true
    }

    fun isImage(fileName: String?, mimeType: String?): Boolean {
        if (isImageMimeType(mimeType)) return true
        val name = fileName?.lowercase(Locale.ROOT) ?: return false
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
               name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".heic") ||
               name.endsWith(".heif") || name.endsWith(".bmp")
    }

    fun getAttachmentsDir(context: Context): File {
        val dir = File(context.filesDir, "attachments")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun openSafeInputStream(context: Context, uri: Uri): java.io.InputStream? {
        return try {
            if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
                val path = uri.path ?: uri.toString().removePrefix("file://")
                File(path).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (_: Exception) {
            try {
                uri.path?.let { File(it).inputStream() }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun copyUriToAppStorage(context: Context, uri: Uri, preferredName: String? = null): String {
        val fileName = sanitizeFileName(preferredName ?: getFileName(context, uri))
        val destFile = File(getAttachmentsDir(context), "${System.currentTimeMillis()}_$fileName")
        val stream = openSafeInputStream(context, uri)
            ?: throw IllegalStateException("Could not read URI: $uri")
        stream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
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

    fun saveTransferBytesToAppStorage(
        context: Context,
        bytes: ByteArray,
        preferredName: String
    ): String {
        val safeName = sanitizeFileName(preferredName)
        val destFile = File(getAttachmentsDir(context), "${System.currentTimeMillis()}_$safeName")
        destFile.writeBytes(bytes)
        return destFile.absolutePath
    }

    fun compressImageForTransfer(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280,
        quality: Int = 80
    ): ByteArray? {
        return try {
            // 1. Determine bounds
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openSafeInputStream(context, uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Compute sample size
            var inSampleSize = 1
            var maxSide = maxOf(origWidth, origHeight)
            while (maxSide / 2 >= maxDimension) {
                inSampleSize *= 2
                maxSide /= 2
            }

            // 3. Decode bitmap with downsampling
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val sampledBitmap = openSafeInputStream(context, uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            // 4. Handle EXIF rotation
            var rotationDegrees = 0
            try {
                openSafeInputStream(context, uri)?.use { input ->
                    val exif = android.media.ExifInterface(input)
                    val orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    rotationDegrees = when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (_: Exception) {}

            // 5. Scale down precisely if needed and apply rotation
            val currentMax = maxOf(sampledBitmap.width, sampledBitmap.height)
            val scaleFactor = if (currentMax > maxDimension) {
                maxDimension.toFloat() / currentMax.toFloat()
            } else {
                1f
            }

            val matrix = android.graphics.Matrix().apply {
                if (scaleFactor != 1f) postScale(scaleFactor, scaleFactor)
                if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
            }

            val finalBitmap = if (scaleFactor != 1f || rotationDegrees != 0) {
                android.graphics.Bitmap.createBitmap(
                    sampledBitmap,
                    0,
                    0,
                    sampledBitmap.width,
                    sampledBitmap.height,
                    matrix,
                    true
                ).also {
                    if (it != sampledBitmap) sampledBitmap.recycle()
                }
            } else {
                sampledBitmap
            }

            // 6. Compress to JPEG
            val out = java.io.ByteArrayOutputStream()
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            finalBitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Error compressing image: ${e.localizedMessage}", e)
            null
        }
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
