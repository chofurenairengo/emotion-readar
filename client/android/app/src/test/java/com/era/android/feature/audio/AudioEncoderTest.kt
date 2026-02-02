package com.era.android.feature.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEncoderTest {

    @Test
    fun `addWavHeader produces 44-byte header`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.addWavHeader(pcmData)
        assertEquals(144, result.size) // 44 header + 100 data
    }

    @Test
    fun `addWavHeader contains RIFF identifier`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.addWavHeader(pcmData)
        val riff = String(result, 0, 4)
        assertEquals("RIFF", riff)
    }

    @Test
    fun `addWavHeader contains WAVE format`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.addWavHeader(pcmData)
        val wave = String(result, 8, 4)
        assertEquals("WAVE", wave)
    }

    @Test
    fun `addWavHeader sets correct sample rate`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.addWavHeader(pcmData)
        val sampleRate = ByteBuffer.wrap(result, 24, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(16000, sampleRate)
    }

    @Test
    fun `addWavHeader sets mono channel`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.addWavHeader(pcmData)
        val channels = ByteBuffer.wrap(result, 22, 2)
            .order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(1, channels.toInt())
    }

    @Test
    fun `addWavHeader sets 16-bit depth`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.addWavHeader(pcmData)
        val bitsPerSample = ByteBuffer.wrap(result, 34, 2)
            .order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(16, bitsPerSample.toInt())
    }

    @Test
    fun `addWavHeader sets correct data size`() {
        val pcmData = ByteArray(256)
        val result = AudioEncoder.addWavHeader(pcmData)
        val dataSize = ByteBuffer.wrap(result, 40, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(256, dataSize)
    }

    @Test
    fun `addWavHeader preserves PCM data after header`() {
        val pcmData = byteArrayOf(1, 2, 3, 4, 5)
        val result = AudioEncoder.addWavHeader(pcmData)
        assertArrayEquals(pcmData, result.copyOfRange(44, result.size))
    }

    @Test
    fun `AudioFormat enum values are correct`() {
        assertEquals("wav", AudioFormat.WAV.value)
        assertEquals("opus", AudioFormat.OPUS.value)
        assertEquals("pcm", AudioFormat.PCM.value)
    }

    // Note: encode() メソッドのテストはandroid.util.Base64を使うため
    // androidTestまたはRobolectricで実行する必要がある
}
