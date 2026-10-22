package com.ashuchat.app.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerHelper(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _currentPlayingId = MutableStateFlow<String?>(null)
    val currentPlayingId: StateFlow<String?> = _currentPlayingId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    companion object {
        private const val TAG = "AudioPlayerHelper"
    }

    fun play(messageId: String, localPath: String) {
        if (_currentPlayingId.value == messageId && mediaPlayer != null) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
            return
        }

        stop()

        try {
            val player = MediaPlayer()
            if (localPath.startsWith("content://")) {
                player.setDataSource(context, Uri.parse(localPath))
            } else {
                val file = if (localPath.startsWith("file://")) File(localPath.removePrefix("file://")) else File(localPath)
                if (!file.exists()) {
                    Log.w(TAG, "Audio file does not exist: $localPath")
                    return
                }
                player.setDataSource(file.absolutePath)
            }

            player.prepare()
            applySpeed(player, _speed.value)
            player.start()

            mediaPlayer = player
            _currentPlayingId.value = messageId
            _isPlaying.value = true
            _totalDurationMs.value = player.duration.toLong().coerceAtLeast(1L)
            _progress.value = 0f

            player.setOnCompletionListener {
                stop()
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                stop()
                true
            }

            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Failed playing audio: ${e.localizedMessage}", e)
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressTracker()
        } catch (_: Exception) {}
    }

    fun seekTo(fraction: Float) {
        val player = mediaPlayer ?: return
        val targetMs = (fraction.coerceIn(0f, 1f) * player.duration).toInt()
        try {
            player.seekTo(targetMs)
            _progress.value = fraction.coerceIn(0f, 1f)
            _currentPositionMs.value = targetMs.toLong()
        } catch (_: Exception) {}
    }

    fun toggleSpeed() {
        val nextSpeed = when (_speed.value) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _speed.value = nextSpeed
        mediaPlayer?.let { applySpeed(it, nextSpeed) }
    }

    private fun applySpeed(player: MediaPlayer, speedValue: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = player.playbackParams
                params.speed = speedValue
                player.playbackParams = params
            } catch (_: Exception) {}
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val current = player.currentPosition.toLong()
                    val total = player.duration.toLong().coerceAtLeast(1L)
                    _currentPositionMs.value = current
                    _totalDurationMs.value = total
                    _progress.value = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
                delay(60)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _currentPlayingId.value = null
        _isPlaying.value = false
        _progress.value = 0f
        _currentPositionMs.value = 0L
    }
}
