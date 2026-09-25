package com.melonapp.an_rewind_recorder.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.melonapp.an_rewind_recorder.audio.AudioConstants
import com.melonapp.an_rewind_recorder.audio.AudioRingBuffer
import com.melonapp.an_rewind_recorder.audio.WavExporter
import com.melonapp.an_rewind_recorder.util.NotificationHelper
import com.melonapp.an_rewind_recorder.util.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

/**
 * Foreground Service that continuously listens into an in-memory AudioRingBuffer.
 * Handles partial wake locks, audio focus, notification actions, and WAV export.
 */
class BacktrackService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val ACTION_START = "com.melonapp.an_rewind_recorder.ACTION_START"
        const val ACTION_STOP = "com.melonapp.an_rewind_recorder.ACTION_STOP"
        const val ACTION_SAVE_5 = "com.melonapp.an_rewind_recorder.ACTION_SAVE_5"
        const val ACTION_SAVE_10 = "com.melonapp.an_rewind_recorder.ACTION_SAVE_10"

        fun startListening(context: Context) {
            val intent = Intent(context, BacktrackService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopListening(context: Context) {
            val intent = Intent(context, BacktrackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun saveLast5Minutes(context: Context) {
            val intent = Intent(context, BacktrackService::class.java).apply {
                action = ACTION_SAVE_5
            }
            context.startService(intent)
        }

        fun saveLast10Minutes(context: Context) {
            val intent = Intent(context, BacktrackService::class.java).apply {
                action = ACTION_SAVE_10
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val ringBuffer = AudioRingBuffer()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private var isPaused = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var lastNotificationUpdateTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        acquireWakeLock()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            ACTION_SAVE_5 -> handleSaveSnapshot(AudioConstants.FIVE_MINUTES_SECONDS, "Last 5 Min")
            ACTION_SAVE_10 -> handleSaveSnapshot(AudioConstants.MAX_BUFFER_DURATION_SECONDS, "Full Buffer (10 Min)")
            else -> handleStart()
        }
        return START_STICKY
    }

    private fun handleStart() {
        if (isRecording) return

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        startInForeground()
        requestAudioFocus()
        startAudioRecording()
    }

    private fun startInForeground() {
        val notification = NotificationHelper.buildForegroundNotification(
            this,
            isPaused = false,
            bufferedDurationText = "00:00 / 10:00"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }

        BacktrackState.setServiceRunning(true)
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConstants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                stopSelf()
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            isPaused = false
            BacktrackState.setServiceRunning(true)
            BacktrackState.setPaused(false)

            recordingJob = serviceScope.launch {
                val readBuffer = ByteArray(bufferSize)
                var bytesAccumulator = 0L

                while (isActive && isRecording) {
                    if (isPaused) {
                        delay(150)
                        continue
                    }

                    val currentRecord = audioRecord ?: break
                    val bytesRead = currentRecord.read(readBuffer, 0, readBuffer.size)

                    if (bytesRead > 0) {
                        ringBuffer.write(readBuffer, 0, bytesRead)
                        bytesAccumulator += bytesRead

                        // Compute RMS amplitude for visualizer (normalized 0.0 - 1.0)
                        val amplitude = computeRmsAmplitude(readBuffer, bytesRead)
                        BacktrackState.updateAmplitude(amplitude)

                        // Update buffer metrics every ~0.5 second of audio
                        if (bytesAccumulator >= AudioConstants.BYTES_PER_SECOND / 2) {
                            bytesAccumulator = 0
                            val availBytes = ringBuffer.getAvailableBytes()
                            val availSecs = ringBuffer.getAvailableDurationSeconds().toInt()
                            val mb = ringBuffer.getMemoryUsageMb()
                            BacktrackState.updateBufferMetrics(availBytes, availSecs, mb)

                            updateNotificationIfNeeded(availSecs)
                        }
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION ||
                        bytesRead == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        delay(100)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun computeRmsAmplitude(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        val sampleCount = length / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until length - 1 step 2) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt() shl 8
            val sample = (high or low).toShort()
            sum += sample.toDouble() * sample.toDouble()
        }

        val rms = sqrt(sum / sampleCount)
        // Normalize against max short value (32768) with non-linear boost for responsiveness
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        return (normalized * 2.5f).coerceAtMost(1f)
    }

    private fun updateNotificationIfNeeded(seconds: Int) {
        val now = System.currentTimeMillis()
        // Throttle notification updates to every 4 seconds to conserve resources
        if (now - lastNotificationUpdateTime > 4000) {
            lastNotificationUpdateTime = now
            val formatted = String.format("%02d:%02d / 10:00", seconds / 60, seconds % 60)
            val notification = NotificationHelper.buildForegroundNotification(
                this,
                isPaused = isPaused,
                bufferedDurationText = formatted
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun handleSaveSnapshot(requestedSeconds: Int, label: String) {
        serviceScope.launch {
            BacktrackState.setExporting(true)
            val availableSeconds = ringBuffer.getAvailableDurationSeconds()

            if (availableSeconds <= 0.0) {
                mainHandler.post {
                    Toast.makeText(applicationContext, "No audio buffered yet to save!", Toast.LENGTH_SHORT).show()
                }
                BacktrackState.emitExportEvent(ExportEvent.Error("No audio buffered yet"))
                BacktrackState.setExporting(false)
                return@launch
            }

            val result = WavExporter.exportSnapshot(
                context = applicationContext,
                ringBuffer = ringBuffer,
                requestedSeconds = requestedSeconds
            )

            result.onSuccess { file ->
                val savedSec = minOf(requestedSeconds.toDouble(), availableSeconds)
                val durationFormatted = String.format("%02d:%02d", (savedSec / 60).toInt(), (savedSec % 60).toInt())
                VibrationHelper.vibrateConfirmation(applicationContext)

                mainHandler.post {
                    Toast.makeText(
                        applicationContext,
                        "Captured $label ($durationFormatted saved to ${file.name})",
                        Toast.LENGTH_LONG
                    ).show()
                }

                BacktrackState.emitExportEvent(ExportEvent.Success(file, savedSec))
            }.onFailure { error ->
                mainHandler.post {
                    Toast.makeText(
                        applicationContext,
                        "Failed to save audio: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                BacktrackState.emitExportEvent(ExportEvent.Error(error.localizedMessage ?: "Export failed"))
            }

            BacktrackState.setExporting(false)
        }
    }

    private fun handleStop() {
        stopAudioRecording()
        abandonAudioFocus()
        releaseWakeLock()
        BacktrackState.setServiceRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopAudioRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Backtrack:AudioRecordingWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            audioFocusRequest?.let { am.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Cellular call, assistant, camera video: pause recording loop
                isPaused = true
                BacktrackState.setPaused(true)
                val notification = NotificationHelper.buildForegroundNotification(
                    this,
                    isPaused = true
                )
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NotificationHelper.NOTIFICATION_ID, notification)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus recovered: resume recording
                isPaused = false
                BacktrackState.setPaused(false)
                val availSecs = ringBuffer.getAvailableDurationSeconds().toInt()
                val formatted = String.format("%02d:%02d / 10:00", availSecs / 60, availSecs % 60)
                val notification = NotificationHelper.buildForegroundNotification(
                    this,
                    isPaused = false,
                    bufferedDurationText = formatted
                )
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NotificationHelper.NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onDestroy() {
        handleStop()
        super.onDestroy()
    }
}
