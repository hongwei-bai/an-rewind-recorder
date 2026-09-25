package com.melonapp.an_rewind_recorder.audio

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles encoding raw 16-bit PCM audio into standard 44-byte RIFF/WAVE (.wav) files
 * and saving them to app-specific storage.
 */
object WavExporter {

    private const val HEADER_SIZE = 44

    /**
     * Synthesizes a standard 44-byte RIFF/WAVE header for 16-bit PCM Mono audio at 16,000 Hz.
     */
    fun createWavHeader(
        pcmDataSize: Int,
        sampleRate: Int = AudioConstants.SAMPLE_RATE,
        channels: Int = AudioConstants.CHANNELS,
        bitsPerSample: Int = AudioConstants.BITS_PER_SAMPLE
    ): ByteArray {
        val totalDataLen = pcmDataSize + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)

        val header = ByteBuffer.allocate(HEADER_SIZE).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            // RIFF chunk descriptor
            put('R'.code.toByte())
            put('I'.code.toByte())
            put('F'.code.toByte())
            put('F'.code.toByte())
            putInt(totalDataLen) // Total file length - 8
            put('W'.code.toByte())
            put('A'.code.toByte())
            put('V'.code.toByte())
            put('E'.code.toByte())

            // "fmt " sub-chunk
            put('f'.code.toByte())
            put('m'.code.toByte())
            put('t'.code.toByte())
            put(' '.code.toByte())
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat 1 = PCM
            putShort(channels.toShort()) // NumChannels
            putInt(sampleRate) // SampleRate
            putInt(byteRate) // ByteRate
            putShort(blockAlign.toShort()) // BlockAlign
            putShort(bitsPerSample.toShort()) // BitsPerSample

            // "data" sub-chunk
            put('d'.code.toByte())
            put('a'.code.toByte())
            put('t'.code.toByte())
            put('a'.code.toByte())
            putInt(pcmDataSize) // Subchunk2Size
        }

        return header.array()
    }

    /**
     * Saves raw PCM byte array to a timestamped .wav file in the app's recordings directory.
     *
     * @param context Android context to access external storage directory.
     * @param pcmData Raw PCM audio byte array.
     * @return Result containing the saved File on success, or an Exception on failure.
     */
    fun saveWavFile(context: Context, pcmData: ByteArray): Result<File> {
        return try {
            if (pcmData.isEmpty()) {
                return Result.failure(IllegalArgumentException("Cannot save empty audio buffer"))
            }

            val recordingsDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
                ?: File(context.filesDir, "recordings")

            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "Backtrack_${timeStamp}.wav"
            val targetFile = File(recordingsDir, fileName)

            val header = createWavHeader(pcmData.size)

            FileOutputStream(targetFile).use { fos ->
                fos.write(header)
                fos.write(pcmData)
                fos.flush()
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts snapshot from ring buffer and saves it as a .wav file.
     */
    fun exportSnapshot(
        context: Context,
        ringBuffer: AudioRingBuffer,
        requestedSeconds: Int
    ): Result<File> {
        val pcmData = ringBuffer.getSnapshotBySeconds(requestedSeconds)
        return saveWavFile(context, pcmData)
    }
}
