package com.commuxr.android.feature.audio

import android.Manifest
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.commuxr.android.core.model.AudioData
import com.commuxr.android.core.model.AudioFormat
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 録音状態
 */
sealed class RecordingState {
    /** 待機中（何もしていない） */
    object Idle : RecordingState()
    /** 音声モニタリング中（録音はまだ開始していない） */
    object Monitoring : RecordingState()
    /** 録音中 */
    object Recording : RecordingState()
    /** 録音完了（AudioData取得可能） */
    data class Completed(val audioData: AudioData) : RecordingState()
    /** エラー発生 */
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
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT

        /** 最大録音時間（30秒） */
        const val MAX_DURATION_MS = 30000L
        /** 音量検出閾値（この値を超えたら音声ありと判定） */
        const val VAD_THRESHOLD = 500
        /** 無音タイムアウト（この時間無音が続いたら録音停止） */
        const val SILENCE_TIMEOUT_MS = 1500L
        /** スレッド終了待ちタイムアウト */
        private const val THREAD_JOIN_TIMEOUT_MS = 3000L
    }

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingBuffer: ByteArrayOutputStream? = null
    private val bufferLock = Any()
    private val isMonitoring = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private val threadLock = Any()
    private var monitoringThread: Thread? = null
    private var recordingStartTime: Long = 0L
    private var lastVoiceTime: Long = 0L

    /**
     * 音声モニタリングを開始
     *
     * 音量を監視し、閾値を超えたら自動的に録音を開始する。
     * 録音は無音が続くか、最大時間に達すると自動停止する。
     *
     * @throws SecurityException RECORD_AUDIO権限がない場合
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startMonitoring() {
        if (isMonitoring.get()) {
            Log.w(TAG, "Already monitoring")
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

            try {
                record.startRecording()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to start monitoring", e)
                record.release()
                audioRecord = null
                _state.value = RecordingState.Error("Failed to start monitoring")
                return
            }

            isMonitoring.set(true)
            _state.value = RecordingState.Monitoring

            synchronized(threadLock) {
                monitoringThread = Thread {
                    monitorAudioData(record, bufferSize)
                }.also { it.start() }
            }

            Log.i(TAG, "Monitoring started (${SAMPLE_RATE}Hz, mono, PCM16)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied", e)
            _state.value = RecordingState.Error("RECORD_AUDIO permission not granted")
            throw e
        }
    }

    /**
     * 音声モニタリングを停止
     */
    fun stopMonitoring() {
        if (!isMonitoring.get()) {
            Log.w(TAG, "Not monitoring")
            return
        }

        isMonitoring.set(false)

        // モニタリングスレッドの終了を待つ（スレッド内で録音停止処理が完了するのを保証）
        val thread = synchronized(threadLock) {
            monitoringThread.also { monitoringThread = null }
        }
        try {
            thread?.join(THREAD_JOIN_TIMEOUT_MS)
            if (thread?.isAlive == true) {
                Log.e(TAG, "Monitoring thread did not terminate within timeout, interrupting")
                thread.interrupt()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Interrupted while waiting for monitoring thread", e)
        }

        isRecording.set(false)

        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error stopping monitoring", e)
        }

        audioRecord?.release()
        audioRecord = null
        _durationMs.value = 0L

        if (_state.value !is RecordingState.Completed) {
            _state.value = RecordingState.Idle
        }

        Log.i(TAG, "Monitoring stopped")
    }

    /**
     * 録音経過時間を取得（ミリ秒）
     */
    fun getDurationMs(): Long {
        return if (isRecording.get() && recordingStartTime > 0) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            _durationMs.value
        }
    }

    /**
     * 録音を開始（手動）
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
            recordingStartTime = System.currentTimeMillis()

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
     *
     * @param generateAudioData trueの場合、AudioDataを生成してCompletedステートに遷移
     */
    fun stopRecording(generateAudioData: Boolean = false) {
        if (!isRecording.get()) {
            Log.w(TAG, "Not recording")
            return
        }

        isRecording.set(false)
        val duration = getDurationMs()
        _durationMs.value = duration

        if (!isMonitoring.get()) {
            try {
                audioRecord?.stop()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error stopping recording", e)
            }

            audioRecord?.release()
            audioRecord = null
        }

        if (generateAudioData) {
            try {
                val audioData = generateAudioData(duration)
                _state.value = RecordingState.Completed(audioData)
                Log.i(TAG, "Recording completed: ${duration}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate audio data", e)
                _state.value = RecordingState.Error("Failed to generate audio data: ${e.message}")
            }
        } else {
            _state.value = if (isMonitoring.get()) {
                RecordingState.Monitoring
            } else {
                RecordingState.Idle
            }
            Log.i(TAG, "Recording stopped: ${duration}ms")
        }
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
     * AudioDataを生成
     */
    private fun generateAudioData(durationMs: Long): AudioData {
        val buffer = recordingBuffer
            ?: throw IllegalStateException("No recording data available")

        val pcmData = synchronized(bufferLock) {
            buffer.toByteArray()
        }

        if (pcmData.isEmpty()) {
            throw IllegalStateException("Recording data is empty")
        }

        val wavData = AudioEncoder.pcmToWav(pcmData, SAMPLE_RATE, 1, 16)
        val base64Data = AudioEncoder.toBase64(wavData)

        return AudioData(
            base64Data = base64Data,
            format = AudioFormat.WAV,
            durationMs = durationMs
        )
    }

    /**
     * リソースを解放
     */
    fun release() {
        stopMonitoring()
        stopRecording()
        synchronized(bufferLock) {
            recordingBuffer = null
        }
        synchronized(threadLock) {
            monitoringThread = null
        }
        _state.value = RecordingState.Idle
    }

    /**
     * 音声モニタリングループ（バックグラウンドスレッド）
     *
     * 音量を監視し、閾値を超えたら録音開始、無音が続いたら録音停止
     */
    private fun monitorAudioData(record: AudioRecord, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)

        while (isMonitoring.get()) {
            val bytesRead = record.read(buffer, 0, bufferSize)

            if (bytesRead > 0) {
                val voiceDetected = isVoiceDetected(buffer, bytesRead)
                val currentTime = System.currentTimeMillis()

                when {
                    // 音声検出 & 録音していない → 録音開始
                    voiceDetected && !isRecording.get() -> {
                        startActualRecording()
                        lastVoiceTime = currentTime
                        synchronized(bufferLock) {
                            recordingBuffer?.write(buffer, 0, bytesRead)
                        }
                    }
                    // 録音中 & 音声あり → バッファに追加
                    voiceDetected && isRecording.get() -> {
                        lastVoiceTime = currentTime
                        synchronized(bufferLock) {
                            recordingBuffer?.write(buffer, 0, bytesRead)
                        }
                        updateDuration()
                    }
                    // 録音中 & 音声なし → バッファに追加（無音部分も含める）
                    !voiceDetected && isRecording.get() -> {
                        synchronized(bufferLock) {
                            recordingBuffer?.write(buffer, 0, bytesRead)
                        }
                        updateDuration()

                        // 無音タイムアウトチェック
                        if (currentTime - lastVoiceTime > SILENCE_TIMEOUT_MS) {
                            Log.i(TAG, "Silence timeout reached, stopping recording")
                            stopActualRecording()
                        }
                    }
                }

                // 最大録音時間チェック
                if (isRecording.get() && getDurationMs() >= MAX_DURATION_MS) {
                    Log.i(TAG, "Max duration reached, stopping recording")
                    stopActualRecording()
                }
            } else if (bytesRead < 0) {
                val errorMessage = when (bytesRead) {
                    AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                    AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                    AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                    else -> "UNKNOWN_ERROR($bytesRead)"
                }
                Log.e(TAG, "AudioRecord read error: $errorMessage")
                isMonitoring.set(false)
                _state.value = RecordingState.Error("Read error: $errorMessage")
                break
            }
        }
    }

    /**
     * 実際の録音を開始（モニタリング中に音声検出時）
     */
    private fun startActualRecording() {
        synchronized(bufferLock) {
            recordingBuffer = ByteArrayOutputStream()
        }
        recordingStartTime = System.currentTimeMillis()
        isRecording.set(true)
        _state.value = RecordingState.Recording
        Log.i(TAG, "Auto recording started (voice detected)")
    }

    /**
     * 実際の録音を停止（モニタリング中）
     */
    private fun stopActualRecording() {
        if (!isRecording.get()) return

        isRecording.set(false)
        val duration = getDurationMs()
        _durationMs.value = duration

        try {
            val audioData: AudioData
            synchronized(bufferLock) {
                audioData = generateAudioData(duration)
                recordingBuffer = null
            }
            recordingStartTime = 0L
            _state.value = RecordingState.Completed(audioData)
            // Completed後にMonitoringに戻す（データ消失防止）
            if (isMonitoring.get()) {
                _state.value = RecordingState.Monitoring
                _durationMs.value = 0L
            }
            Log.i(TAG, "Auto recording completed: ${duration}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate audio data", e)
            synchronized(bufferLock) {
                recordingBuffer = null
            }
            recordingStartTime = 0L
            _state.value = RecordingState.Error("Failed to generate audio data: ${e.message}")
        }
    }

    /**
     * 録音時間を更新
     */
    private fun updateDuration() {
        if (recordingStartTime > 0) {
            _durationMs.value = System.currentTimeMillis() - recordingStartTime
        }
    }

    /**
     * 音声が検出されたかどうかを判定（VAD）
     */
    private fun isVoiceDetected(buffer: ByteArray, length: Int): Boolean {
        val amplitude = calculateAmplitude(buffer, length)
        return amplitude > VAD_THRESHOLD
    }

    /**
     * PCMバッファの振幅を計算
     */
    private fun calculateAmplitude(buffer: ByteArray, length: Int): Int {
        if (length < 2) return 0

        var sum = 0L
        val sampleCount = length / 2

        for (i in 0 until length - 1 step 2) {
            // リトルエンディアンで16bit PCMを読み取り、符号付き16bit（Short）として解釈する
            val sample: Short = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += abs(sample.toInt())
        }

        return if (sampleCount > 0) (sum / sampleCount).toInt() else 0
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
