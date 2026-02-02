package com.era.android.feature.audio

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

    // Note: startRecording/stopRecording のテストはAudioRecord APIのため
    // androidTestまたはRobolectricで実行する必要がある
}
