package com.commuxr.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioDataTest {

    @Test
    fun `AudioData holds correct values`() {
        val data = AudioData(
            base64Data = "dGVzdA==",
            format = AudioFormat.WAV,
            durationMs = 5000L
        )
        assertEquals("dGVzdA==", data.base64Data)
        assertEquals(AudioFormat.WAV, data.format)
        assertEquals(5000L, data.durationMs)
    }

    @Test
    fun `AudioFormat WAV has correct value`() {
        assertEquals("wav", AudioFormat.WAV.value)
    }

    @Test
    fun `AudioFormat OPUS has correct value`() {
        assertEquals("opus", AudioFormat.OPUS.value)
    }

    @Test
    fun `AudioFormat PCM has correct value`() {
        assertEquals("pcm", AudioFormat.PCM.value)
    }

    @Test
    fun `AudioFormat enum has exactly 3 entries`() {
        assertEquals(3, AudioFormat.entries.size)
    }

    @Test
    fun `data class equality works correctly`() {
        val data1 = AudioData("dGVzdA==", AudioFormat.WAV, 5000L)
        val data2 = AudioData("dGVzdA==", AudioFormat.WAV, 5000L)
        assertEquals(data1, data2)
    }

    @Test
    fun `copy creates new instance with updated values`() {
        val original = AudioData("dGVzdA==", AudioFormat.WAV, 5000L)
        val updated = original.copy(format = AudioFormat.OPUS)
        assertEquals(AudioFormat.WAV, original.format)
        assertEquals(AudioFormat.OPUS, updated.format)
    }
}
