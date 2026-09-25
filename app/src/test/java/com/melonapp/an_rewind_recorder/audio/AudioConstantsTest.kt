package com.melonapp.an_rewind_recorder.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioConstantsTest {

    @Test
    fun testAudioCalculationAccuracy() {
        // 16,000 samples/sec * 2 bytes/sample = 32,000 bytes/sec
        assertEquals(32000, AudioConstants.BYTES_PER_SECOND)

        // 10-Minute Buffer Size: 32,000 * 60 * 10 = 19,200,000 bytes (~18.31 MB)
        assertEquals(19200000, AudioConstants.MAX_BUFFER_CAPACITY_BYTES)

        // 5-Minute Slice: 32,000 * 60 * 5 = 9,600,000 bytes (~9.15 MB)
        assertEquals(9600000, AudioConstants.FIVE_MINUTES_BYTES)

        // Conversions
        assertEquals(600.0, AudioConstants.bytesToSeconds(19200000), 0.001)
        assertEquals(300.0, AudioConstants.bytesToSeconds(9600000), 0.001)
        assertEquals(19200000, AudioConstants.secondsToBytes(600))
        assertEquals(9600000, AudioConstants.secondsToBytes(300))

        // MB footprint
        val mb = AudioConstants.bytesToMb(19200000)
        assertEquals(18.31f, mb, 0.05f)
    }
}
