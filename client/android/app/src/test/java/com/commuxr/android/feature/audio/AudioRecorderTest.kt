package com.commuxr.android.feature.audio

import com.commuxr.android.core.model.AudioData
import com.commuxr.android.core.model.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRecorderTest {

    @Test
    fun `initial state is Idle`() {
        val recorder = AudioRecorder()
        assertEquals(RecordingState.Idle, recorder.state.value)
    }

    @Test
    fun `initial durationMs is zero`() {
        val recorder = AudioRecorder()
        assertEquals(0L, recorder.durationMs.value)
    }

    @Test
    fun `getDurationMs returns zero when not recording`() {
        val recorder = AudioRecorder()
        assertEquals(0L, recorder.getDurationMs())
    }

    @Test
    fun `getEncodedAudio throws when no data`() {
        val recorder = AudioRecorder()
        assertThrows(IllegalStateException::class.java) {
            recorder.getEncodedAudio()
        }
    }

    @Test
    fun `RecordingState Idle is correct type`() {
        val state: RecordingState = RecordingState.Idle
        assertTrue(state is RecordingState.Idle)
    }

    @Test
    fun `RecordingState Monitoring is correct type`() {
        val state: RecordingState = RecordingState.Monitoring
        assertTrue(state is RecordingState.Monitoring)
    }

    @Test
    fun `RecordingState Recording is correct type`() {
        val state: RecordingState = RecordingState.Recording
        assertTrue(state is RecordingState.Recording)
    }

    @Test
    fun `RecordingState Completed contains AudioData`() {
        val audioData = AudioData(
            base64Data = "dGVzdA==",
            format = AudioFormat.WAV,
            durationMs = 1000L
        )
        val state = RecordingState.Completed(audioData)
        assertEquals(audioData, state.audioData)
        assertEquals(1000L, state.audioData.durationMs)
    }

    @Test
    fun `RecordingState Error contains message`() {
        val state = RecordingState.Error("test error")
        assertEquals("test error", state.message)
    }

    @Test
    fun `release resets state to Idle`() {
        val recorder = AudioRecorder()
        recorder.release()
        assertEquals(RecordingState.Idle, recorder.state.value)
    }

    @Test
    fun `companion object constants are correct`() {
        assertEquals(16000, AudioRecorder.SAMPLE_RATE)
        assertEquals(30000L, AudioRecorder.MAX_DURATION_MS)
        assertEquals(500, AudioRecorder.VAD_THRESHOLD)
        assertEquals(1500L, AudioRecorder.SILENCE_TIMEOUT_MS)
    }

    // Note: startMonitoring/stopMonitoring/startRecording/stopRecording のテストは
    // AudioRecord APIのためandroidTestまたはRobolectricで実行する必要がある
}
