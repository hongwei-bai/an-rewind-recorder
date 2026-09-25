package com.melonapp.an_rewind_recorder.audio

object AudioConstants {
    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8 // 2 bytes

    // 16,000 samples/sec * 2 bytes/sample = 32,000 bytes/sec
    const val BYTES_PER_SECOND = SAMPLE_RATE * BYTES_PER_SAMPLE

    // Buffer durations in seconds
    const val MAX_BUFFER_DURATION_SECONDS = 10 * 60 // 600s (10 minutes)
    const val FIVE_MINUTES_SECONDS = 5 * 60 // 300s (5 minutes)

    // Capacities in bytes
    // 32,000 * 60 * 10 = 19,200,000 bytes (~18.31 MB)
    const val MAX_BUFFER_CAPACITY_BYTES = BYTES_PER_SECOND * MAX_BUFFER_DURATION_SECONDS

    // 32,000 * 60 * 5 = 9,600,000 bytes (~9.15 MB)
    const val FIVE_MINUTES_BYTES = BYTES_PER_SECOND * FIVE_MINUTES_SECONDS

    fun bytesToSeconds(bytes: Int): Double {
        return bytes.toDouble() / BYTES_PER_SECOND
    }

    fun secondsToBytes(seconds: Int): Int {
        return seconds * BYTES_PER_SECOND
    }

    fun bytesToMb(bytes: Int): Float {
        return bytes / (1024f * 1024f)
    }
}
