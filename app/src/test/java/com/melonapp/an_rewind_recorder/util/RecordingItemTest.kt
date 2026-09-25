package com.melonapp.an_rewind_recorder.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RecordingItemTest {

    @Test
    fun testRecordingItemFormattedProperties() {
        val item = RecordingItem(
            file = File("dummy.wav"),
            name = "dummy.wav",
            lastModified = System.currentTimeMillis(),
            sizeBytes = 9_600_044L, // 5 minutes of 16kHz audio + 44 byte header
            durationSeconds = 300.0
        )

        assertEquals("05:00", item.formattedDuration)
        assertEquals("9.2 MB", item.formattedSize)
    }

    @Test
    fun testSmallRecordingItemFormatting() {
        val item = RecordingItem(
            file = File("quick.wav"),
            name = "quick.wav",
            lastModified = System.currentTimeMillis(),
            sizeBytes = 64_044L, // 2 seconds
            durationSeconds = 2.0
        )

        assertEquals("00:02", item.formattedDuration)
        assertEquals("63 KB", item.formattedSize)
    }
}
