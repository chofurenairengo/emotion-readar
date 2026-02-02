package com.era.android.feature.audio

import android.Manifest
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.era.android.core.model.AudioFormat
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 録音状態
 */
sealed class RecordingState {
    data object Idle : RecordingState()
    data object Recording : RecordingState()
    data object Stopped : RecordingState()
    data class Error(val message: String) : RecordingState()
}

/**
 * 音声録音クラス
 *
 * AudioRecord APIを使用して16kHz/モノラル/PCM16bitで録音する。
 * 録音データはByteArrayOutputStreamにバッファリングされ、
 * stopRecording()後にgetEncodedAudio()でBase64データを取得できる。
 *
 * 使用例:
 * ```
 * val recorder = AudioRecorder()
 * recorder.startRecording()
 * // ... 録音中 ...
 * recorder.stopRecording()
 * val (base64Data, format) = recorder.getEncodedAudio(AudioFormat.WAV)
 * ```
 */
class AudioRecorder {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
    }

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingBuffer: ByteArrayOutputStream? = null
    private val bufferLock = Any()
    private val isRecording = AtomicBoolean(false)

    /**
     * 録音を開始
     *
     * @throws SecurityException RECORD_AUDIO権限がない場合
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )

        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            val errorMsg = "Invalid buffer size: $bufferSize"
            Log.e(TAG, errorMsg)
            _state.value = RecordingState.Error(errorMsg)
            return
        }

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                val errorMsg = "AudioRecord initialization failed"
                Log.e(TAG, errorMsg)
                record.release()
                _state.value = RecordingState.Error(errorMsg)
                return
            }

            audioRecord = record
            recordingBuffer = ByteArrayOutputStream()

            try {
                record.startRecording()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to start recording", e)
                record.release()
                audioRecord = null
                recordingBuffer = null
                _state.value = RecordingState.Error("Failed to start recording")
                return
            }

            isRecording.set(true)
            _state.value = RecordingState.Recording

            // バックグラウンドで録音データを読み取る
            Thread {
                readAudioData(record, bufferSize)
            }.start()

            Log.i(TAG, "Recording started (${SAMPLE_RATE}Hz, mono, PCM16)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied", e)
            _state.value = RecordingState.Error("RECORD_AUDIO permission not granted")
            throw e
        }
    }

    /**
     * 録音を停止
     */
    fun stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "Not recording")
            return
        }

        isRecording.set(false)

        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error stopping recording", e)
        }

        audioRecord?.release()
        audioRecord = null
        _state.value = RecordingState.Stopped

        Log.i(TAG, "Recording stopped")
    }

    /**
     * 録音データをBase64エンコードして取得
     *
     * @param format 出力フォーマット（デフォルト: WAV）
     * @return Pair(Base64エンコード文字列, フォーマット文字列)
     * @throws IllegalStateException 録音データがない場合
     */
    fun getEncodedAudio(format: AudioFormat = AudioFormat.WAV): Pair<String, String> {
        val buffer = recordingBuffer
            ?: throw IllegalStateException("No recording data available")

        val pcmData = synchronized(bufferLock) {
            buffer.toByteArray()
        }

        if (pcmData.isEmpty()) {
            throw IllegalStateException("Recording data is empty")
        }

        val encoded = AudioEncoder.encode(pcmData, format)
        return Pair(encoded, format.value)
    }

    /**
     * リソースを解放
     */
    fun release() {
        stopRecording()
        recordingBuffer = null
        _state.value = RecordingState.Idle
    }

    /**
     * 録音データの読み取りループ（バックグラウンドスレッド）
     */
    private fun readAudioData(record: AudioRecord, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        while (isRecording.get()) {
            val bytesRead = record.read(buffer, 0, bufferSize)
            if (bytesRead > 0) {
                synchronized(bufferLock) {
                    recordingBuffer?.write(buffer, 0, bytesRead)
                }
            } else if (bytesRead == 0) {
                try {
                    Thread.sleep(10)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            } else {
                val errorMessage = when (bytesRead) {
                    AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                    AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                    AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                    else -> "UNKNOWN_ERROR($bytesRead)"
                }
                Log.e(TAG, "AudioRecord read error: $errorMessage")
                isRecording.set(false)
                _state.value = RecordingState.Error("Read error: $errorMessage")
                break
            }
        }
    }
}
