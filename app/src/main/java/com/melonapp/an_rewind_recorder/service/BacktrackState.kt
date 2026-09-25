package com.melonapp.an_rewind_recorder.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class ExportEvent {
    data class Success(val file: File, val savedSeconds: Double) : ExportEvent()
    data class Error(val message: String) : ExportEvent()
}

/**
 * Observable global state for BacktrackService communication with Compose UI.
 */
object BacktrackState {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _bufferedSeconds = MutableStateFlow(0)
    val bufferedSeconds: StateFlow<Int> = _bufferedSeconds.asStateFlow()

    private val _bufferedBytes = MutableStateFlow(0)
    val bufferedBytes: StateFlow<Int> = _bufferedBytes.asStateFlow()

    private val _memoryUsageMb = MutableStateFlow(0f)
    val memoryUsageMb: StateFlow<Float> = _memoryUsageMb.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportEvents = MutableSharedFlow<ExportEvent>(extraBufferCapacity = 10)
    val exportEvents: SharedFlow<ExportEvent> = _exportEvents.asSharedFlow()

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _isPaused.value = false
            _bufferedSeconds.value = 0
            _bufferedBytes.value = 0
            _memoryUsageMb.value = 0f
            _currentAmplitude.value = 0f
        }
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
        if (paused) {
            _currentAmplitude.value = 0f
        }
    }

    fun updateBufferMetrics(bytes: Int, seconds: Int, mb: Float) {
        _bufferedBytes.value = bytes
        _bufferedSeconds.value = seconds
        _memoryUsageMb.value = mb
    }

    fun updateAmplitude(amplitude: Float) {
        _currentAmplitude.value = amplitude
    }

    fun setExporting(exporting: Boolean) {
        _isExporting.value = exporting
    }

    fun emitExportEvent(event: ExportEvent) {
        _exportEvents.tryEmit(event)
    }
}
