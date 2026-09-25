package com.melonapp.an_rewind_recorder.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melonapp.an_rewind_recorder.audio.AudioConstants
import com.melonapp.an_rewind_recorder.service.BacktrackService
import com.melonapp.an_rewind_recorder.service.BacktrackState
import com.melonapp.an_rewind_recorder.service.ExportEvent
import com.melonapp.an_rewind_recorder.util.AudioPlayerManager
import com.melonapp.an_rewind_recorder.util.BatteryOptimizationHelper
import com.melonapp.an_rewind_recorder.util.PlaybackState
import com.melonapp.an_rewind_recorder.util.RecordingItem
import com.melonapp.an_rewind_recorder.util.RecordingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class BacktrackViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val repository = RecordingsRepository(context)
    val audioPlayerManager = AudioPlayerManager()

    val isListening: StateFlow<Boolean> = BacktrackState.isServiceRunning
    val isPaused: StateFlow<Boolean> = BacktrackState.isPaused
    val bufferedSeconds: StateFlow<Int> = BacktrackState.bufferedSeconds
    val bufferedBytes: StateFlow<Int> = BacktrackState.bufferedBytes
    val memoryUsageMb: StateFlow<Float> = BacktrackState.memoryUsageMb
    val currentAmplitude: StateFlow<Float> = BacktrackState.currentAmplitude
    val isExporting: StateFlow<Boolean> = BacktrackState.isExporting
    val playbackState: StateFlow<PlaybackState> = audioPlayerManager.playbackState

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(true)
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    init {
        refreshRecordings()
        refreshBatteryOptimizationStatus()

        // Observe export events from service to refresh list automatically
        viewModelScope.launch {
            BacktrackState.exportEvents.collect { event ->
                if (event is ExportEvent.Success) {
                    refreshRecordings()
                }
            }
        }
    }

    fun refreshBatteryOptimizationStatus() {
        _isIgnoringBatteryOptimizations.value =
            BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    fun refreshRecordings() {
        viewModelScope.launch {
            _recordings.value = repository.loadRecordings()
        }
    }

    fun startListening() {
        BacktrackService.startListening(context)
    }

    fun stopListening() {
        BacktrackService.stopListening(context)
    }

    fun saveLast5Minutes() {
        BacktrackService.saveLast5Minutes(context)
    }

    fun saveLast10Minutes() {
        BacktrackService.saveLast10Minutes(context)
    }

    fun playRecording(file: File) {
        audioPlayerManager.playFile(file)
    }

    fun pausePlayback() {
        audioPlayerManager.pause()
    }

    fun seekPlayback(positionMs: Int) {
        audioPlayerManager.seekTo(positionMs)
    }

    fun deleteRecording(item: RecordingItem) {
        if (playbackState.value.currentFile?.absolutePath == item.file.absolutePath) {
            audioPlayerManager.stop()
        }
        repository.deleteRecording(item.file)
        refreshRecordings()
    }

    fun createShareIntent(file: File) = repository.createShareIntent(file)

    override fun onCleared() {
        audioPlayerManager.release()
        super.onCleared()
    }
}
