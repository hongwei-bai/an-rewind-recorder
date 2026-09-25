package com.melonapp.an_rewind_recorder.audio

import kotlin.math.min

/**
 * Thread-safe circular in-memory audio ring buffer.
 *
 * Continuously stores raw 16-bit PCM audio. When buffer reaches capacity (10 minutes, ~19.2 MB),
 * older audio is overwritten in a FIFO manner with zero disk wear.
 */
class AudioRingBuffer(
    val capacity: Int = AudioConstants.MAX_BUFFER_CAPACITY_BYTES
) {
    private val lock = Any()
    private val buffer = ByteArray(capacity)
    private var head: Int = 0 // Next write position
    private var totalBytesWritten: Long = 0L

    /**
     * Appends raw PCM audio bytes to the ring buffer.
     * Overwrites the oldest samples when capacity is reached.
     */
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        if (length <= 0) return

        synchronized(lock) {
            var actualOffset = offset
            var actualLength = length

            // If incoming data is larger than entire buffer, only keep the newest 'capacity' bytes
            if (actualLength > capacity) {
                actualOffset += (actualLength - capacity)
                actualLength = capacity
            }

            val firstChunk = min(actualLength, capacity - head)
            System.arraycopy(data, actualOffset, buffer, head, firstChunk)

            val secondChunk = actualLength - firstChunk
            if (secondChunk > 0) {
                System.arraycopy(data, actualOffset + firstChunk, buffer, 0, secondChunk)
            }

            head = (head + actualLength) % capacity
            totalBytesWritten += actualLength
        }
    }

    /**
     * Returns the current number of valid audio bytes stored in the buffer.
     */
    fun getAvailableBytes(): Int {
        synchronized(lock) {
            return min(totalBytesWritten, capacity.toLong()).toInt()
        }
    }

    /**
     * Returns the duration of valid audio currently stored in the buffer in seconds.
     */
    fun getAvailableDurationSeconds(): Double {
        return AudioConstants.bytesToSeconds(getAvailableBytes())
    }

    /**
     * Returns the current RAM footprint in Megabytes (MB).
     */
    fun getMemoryUsageMb(): Float {
        return AudioConstants.bytesToMb(getAvailableBytes())
    }

    /**
     * Extracts an ordered snapshot of the most recent audio up to [requestedSeconds].
     * If the requested duration exceeds the available audio in the buffer,
     * it gracefully returns whatever audio is currently available.
     */
    fun getSnapshotBySeconds(requestedSeconds: Int): ByteArray {
        val requestedBytes = AudioConstants.secondsToBytes(requestedSeconds)
        return getSnapshot(requestedBytes)
    }

    /**
     * Extracts an ordered snapshot of the most recent [requestedBytes] of PCM data.
     * Returns PCM bytes in chronological order (oldest to newest within the slice).
     */
    fun getSnapshot(requestedBytes: Int): ByteArray {
        synchronized(lock) {
            val available = min(totalBytesWritten, capacity.toLong()).toInt()
            if (available == 0 || requestedBytes <= 0) {
                return ByteArray(0)
            }

            var bytesToRead = min(requestedBytes, available)
            // Ensure 16-bit sample alignment (2 bytes per sample)
            val sampleRemainder = bytesToRead % AudioConstants.BYTES_PER_SAMPLE
            if (sampleRemainder != 0) {
                bytesToRead -= sampleRemainder
            }

            if (bytesToRead <= 0) {
                return ByteArray(0)
            }

            val result = ByteArray(bytesToRead)
            // Calculate start index for the requested most recent bytes
            var startOffset = (head - bytesToRead) % capacity
            if (startOffset < 0) {
                startOffset += capacity
            }

            val firstChunk = min(bytesToRead, capacity - startOffset)
            System.arraycopy(buffer, startOffset, result, 0, firstChunk)

            val secondChunk = bytesToRead - firstChunk
            if (secondChunk > 0) {
                System.arraycopy(buffer, 0, result, firstChunk, secondChunk)
            }

            return result
        }
    }

    /**
     * Clears all recorded audio in the buffer.
     */
    fun clear() {
        synchronized(lock) {
            head = 0
            totalBytesWritten = 0L
        }
    }
}
