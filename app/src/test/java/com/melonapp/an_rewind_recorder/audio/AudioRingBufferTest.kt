package com.melonapp.an_rewind_recorder.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRingBufferTest {

    @Test
    fun testEmptyBufferReturnsEmpty() {
        val ringBuffer = AudioRingBuffer(capacity = 100)
        assertEquals(0, ringBuffer.getAvailableBytes())
        val snapshot = ringBuffer.getSnapshot(50)
        assertEquals(0, snapshot.size)
    }

    @Test
    fun testWriteLessThanCapacity() {
        val ringBuffer = AudioRingBuffer(capacity = 100)
        val data = byteArrayOf(1, 2, 3, 4, 5, 6)
        ringBuffer.write(data)

        assertEquals(6, ringBuffer.getAvailableBytes())
        val snapshot = ringBuffer.getSnapshot(6)
        assertArrayEquals(data, snapshot)
    }

    @Test
    fun testRequestMoreThanAvailableGracefullyReturnsAllAvailable() {
        val ringBuffer = AudioRingBuffer(capacity = 100)
        val data = byteArrayOf(10, 20, 30, 40)
        ringBuffer.write(data)

        // Requesting 50 bytes when only 4 are available
        val snapshot = ringBuffer.getSnapshot(50)
        assertEquals(4, snapshot.size)
        assertArrayEquals(data, snapshot)
    }

    @Test
    fun testRequestRecentSubset() {
        val ringBuffer = AudioRingBuffer(capacity = 100)
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        ringBuffer.write(data)

        // Request last 4 bytes (must be 5, 6, 7, 8)
        val snapshot = ringBuffer.getSnapshot(4)
        assertEquals(4, snapshot.size)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), snapshot)
    }

    @Test
    fun testWrapAroundBufferOverwritesOldest() {
        val capacity = 6
        val ringBuffer = AudioRingBuffer(capacity = capacity)

        // Write 4 bytes: [1, 2, 3, 4]
        ringBuffer.write(byteArrayOf(1, 2, 3, 4))
        assertEquals(4, ringBuffer.getAvailableBytes())

        // Write 4 more bytes: [5, 6, 7, 8]
        // Total written = 8, capacity = 6.
        // Oldest (1, 2) overwritten. Buffer should contain [3, 4, 5, 6, 7, 8]
        ringBuffer.write(byteArrayOf(5, 6, 7, 8))
        assertEquals(6, ringBuffer.getAvailableBytes())

        val snapshotFull = ringBuffer.getSnapshot(6)
        assertArrayEquals(byteArrayOf(3, 4, 5, 6, 7, 8), snapshotFull)

        // Request last 4 bytes: should be [5, 6, 7, 8]
        val snapshot4 = ringBuffer.getSnapshot(4)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), snapshot4)

        // Request last 2 bytes: should be [7, 8]
        val snapshot2 = ringBuffer.getSnapshot(2)
        assertArrayEquals(byteArrayOf(7, 8), snapshot2)
    }

    @Test
    fun testSampleAlignmentIsPreserved() {
        val ringBuffer = AudioRingBuffer(capacity = 100)
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        ringBuffer.write(data)

        // If 5 bytes are requested, it should round down to 4 bytes to preserve 2-byte sample alignment
        val snapshot = ringBuffer.getSnapshot(5)
        assertEquals(4, snapshot.size)
        // Most recent 4 bytes from [1, 2, 3, 4, 5, 6, 7] -> 7 bytes available, aligned requested is 4
        // last 4 bytes are 4, 5, 6, 7
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), snapshot)
    }

    @Test
    fun testClearResetsBuffer() {
        val ringBuffer = AudioRingBuffer(capacity = 100)
        ringBuffer.write(byteArrayOf(1, 2, 3, 4))
        assertEquals(4, ringBuffer.getAvailableBytes())

        ringBuffer.clear()
        assertEquals(0, ringBuffer.getAvailableBytes())
        assertEquals(0, ringBuffer.getSnapshot(10).size)
    }
}
