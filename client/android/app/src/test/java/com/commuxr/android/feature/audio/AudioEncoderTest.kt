package com.commuxr.android.feature.audio

import com.commuxr.android.core.model.AudioFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEncoderTest {

    @Test
    fun `pcmToWav produces 44-byte header`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.pcmToWav(pcmData)
        assertEquals(144, result.size) // 44 header + 100 data
    }

    @Test
    fun `pcmToWav contains RIFF identifier`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.pcmToWav(pcmData)
        val riff = String(result, 0, 4)
        assertEquals("RIFF", riff)
    }

    @Test
    fun `pcmToWav contains WAVE format`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.pcmToWav(pcmData)
        val wave = String(result, 8, 4)
        assertEquals("WAVE", wave)
    }

    @Test
    fun `pcmToWav sets correct default sample rate`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.pcmToWav(pcmData)
        val sampleRate = ByteBuffer.wrap(result, 24, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(16000, sampleRate)
    }

    @Test
    fun `pcmToWav sets custom sample rate`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.pcmToWav(pcmData, sampleRate = 44100)
        val sampleRate = ByteBuffer.wrap(result, 24, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(44100, sampleRate)
    }

    @Test
    fun `pcmToWav sets mono channel by default`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.pcmToWav(pcmData)
        val channels = ByteBuffer.wrap(result, 22, 2)
            .order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(1, channels.toInt())
    }

    @Test
    fun `pcmToWav sets custom channel count`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.pcmToWav(pcmData, channels = 2)
        val channels = ByteBuffer.wrap(result, 22, 2)
            .order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(2, channels.toInt())
    }

    @Test
    fun `pcmToWav sets 16-bit depth by default`() {
        val pcmData = ByteArray(100)
        val result = AudioEncoder.pcmToWav(pcmData)
        val bitsPerSample = ByteBuffer.wrap(result, 34, 2)
            .order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(16, bitsPerSample.toInt())
    }

    @Test
    fun `pcmToWav sets correct data size`() {
        val pcmData = ByteArray(256)
        val result = AudioEncoder.pcmToWav(pcmData)
        val dataSize = ByteBuffer.wrap(result, 40, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(256, dataSize)
    }

    @Test
    fun `pcmToWav preserves PCM data after header`() {
        val pcmData = byteArrayOf(1, 2, 3, 4, 5)
        val result = AudioEncoder.pcmToWav(pcmData)
        assertArrayEquals(pcmData, result.copyOfRange(44, result.size))
    }

    @Test
    fun `pcmToWav calculates correct byte rate`() {
        val pcmData = ByteArray(100)
        // byteRate = sampleRate * channels * bitsPerSample / 8
        // 16000 * 1 * 16 / 8 = 32000
        val result = AudioEncoder.pcmToWav(pcmData)
        val byteRate = ByteBuffer.wrap(result, 28, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(32000, byteRate)
    }

    @Test
    fun `AudioFormat enum values are correct`() {
        assertEquals("wav", AudioFormat.WAV.value)
        assertEquals("opus", AudioFormat.OPUS.value)
        assertEquals("pcm", AudioFormat.PCM.value)
    }

    // Note: encode() / toBase64() メソッドのテストはandroid.util.Base64を使うため
    // androidTestまたはRobolectricで実行する必要がある
}
