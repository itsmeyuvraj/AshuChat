package com.ashuchat.app.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMillis: Long = 0L

    companion object {
        private const val TAG = "AudioRecorderHelper"
    }

    fun startRecording(): Boolean {
        return try {
            val storageDir = FileUtils.getAttachmentsDir(context)
            outputFile = File(storageDir, "voice_${System.currentTimeMillis()}.m4a")

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }

            recorder = mr
            startTimeMillis = System.currentTimeMillis()
            Log.d(TAG, "Audio recording started: ${outputFile?.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.localizedMessage}", e)
            stopRecording(discard = true)
            false
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun stopRecording(discard: Boolean = false): Pair<File, Long>? {
        val file = outputFile
        val duration = System.currentTimeMillis() - startTimeMillis
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recorder: ${e.localizedMessage}")
        }
        recorder = null
        outputFile = null

        if (discard || file == null || !file.exists() || file.length() == 0L || duration < 500) {
            file?.delete()
            return null
        }

        return Pair(file, duration)
    }

    val isRecording: Boolean
        get() = recorder != null
}
