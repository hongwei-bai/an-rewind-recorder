package com.melonapp.an_rewind_recorder.util

import android.media.MediaPlayer
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

data class PlaybackState(
    val currentFile: File? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

class AudioPlayerManager {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun playFile(file: File) {
        val currentState = _playbackState.value

        // If same file and paused, resume playback
        if (currentState.currentFile?.absolutePath == file.absolutePath && currentState.isPaused) {
            mediaPlayer?.start()
            _playbackState.value = currentState.copy(isPlaying = true, isPaused = false)
            startProgressUpdates()
            return
        }

        // Otherwise reset and load new file
        stop()

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _playbackState.value = PlaybackState(
                        currentFile = file,
                        isPlaying = false,
                        isPaused = false,
                        currentPositionMs = duration,
                        durationMs = duration
                    )
                    stopProgressUpdates()
                }
                start()
            }
            mediaPlayer = player

            _playbackState.value = PlaybackState(
                currentFile = file,
                isPlaying = true,
                isPaused = false,
                currentPositionMs = 0,
                durationMs = player.duration
            )

            startProgressUpdates()
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = true,
                    currentPositionMs = it.currentPosition
                )
                stopProgressUpdates()
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            it.seekTo(positionMs)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        }
    }

    fun stop() {
        stopProgressUpdates()
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        _playbackState.value = PlaybackState()
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = player.currentPosition,
                            durationMs = player.duration
                        )
                    }
                }
                delay(150)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stop()
    }
}
