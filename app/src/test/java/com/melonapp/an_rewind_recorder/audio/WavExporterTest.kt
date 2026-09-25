package com.melonapp.an_rewind_recorder.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavExporterTest {

    @Test
    fun testWavHeaderFormat() {
        val pcmDataSize = 32000 // 1 second of 16kHz 16-bit mono
        val header = WavExporter.createWavHeader(pcmDataSize)

        assertEquals(44, header.size)

        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF descriptor
        val riff = ByteArray(4)
        bb.get(riff)
        assertEquals("RIFF", String(riff))

        val totalDataLen = bb.getInt()
        assertEquals(pcmDataSize + 36, totalDataLen)

        val wave = ByteArray(4)
        bb.get(wave)
        assertEquals("WAVE", String(wave))

        // "fmt " chunk
        val fmt = ByteArray(4)
        bb.get(fmt)
        assertEquals("fmt ", String(fmt))

        val subchunk1Size = bb.getInt()
        assertEquals(16, subchunk1Size)

        val audioFormat = bb.getShort()
        assertEquals(1.toShort(), audioFormat) // PCM

        val numChannels = bb.getShort()
        assertEquals(1.toShort(), numChannels) // Mono

        val sampleRate = bb.getInt()
        assertEquals(16000, sampleRate)

        val byteRate = bb.getInt()
        assertEquals(32000, byteRate)

        val blockAlign = bb.getShort()
        assertEquals(2.toShort(), blockAlign)

        val bitsPerSample = bb.getShort()
        assertEquals(16.toShort(), bitsPerSample)

        // "data" chunk
        val dataChunkId = ByteArray(4)
        bb.get(dataChunkId)
        assertEquals("data", String(dataChunkId))

        val subchunk2Size = bb.getInt()
        assertEquals(pcmDataSize, subchunk2Size)
    }
}
