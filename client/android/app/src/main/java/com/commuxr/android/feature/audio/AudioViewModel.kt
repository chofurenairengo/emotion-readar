package com.commuxr.android.feature.audio

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commuxr.android.core.model.AudioData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 音声録音の状態管理を担当するViewModel
 *
 * 音声モニタリングを開始すると、音量を監視し、
 * 閾値を超えたら自動的に録音を開始する。
 * 無音が続くか、最大時間（30秒）に達すると自動停止。
 */
class AudioViewModel : ViewModel() {

    /**
     * 音声録音のUI状態
     *
     * @param isMonitoring 音声モニタリング中かどうか
     * @param isRecording 録音中かどうか
     * @param durationMs 録音経過時間（ミリ秒）
     * @param lastRecordedAudio 最後に録音されたAudioData
     * @param errorMessage エラーメッセージ（nullの場合はエラーなし）
     */
    data class UiState(
        val isMonitoring: Boolean = false,
        val isRecording: Boolean = false,
        val durationMs: Long = 0,
        val lastRecordedAudio: AudioData? = null,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val audioRecorder = AudioRecorder()

    init {
        viewModelScope.launch {
            audioRecorder.state.collect { state ->
                when (state) {
                    is RecordingState.Idle -> {
                        _uiState.update {
                            it.copy(
                                isMonitoring = false,
                                isRecording = false,
                                durationMs = 0,
                                errorMessage = null
                            )
                        }
                    }
                    is RecordingState.Monitoring -> {
                        _uiState.update {
                            it.copy(
                                isMonitoring = true,
                                isRecording = false,
                                durationMs = 0,
                                errorMessage = null
                            )
                        }
                    }
                    is RecordingState.Recording -> {
                        _uiState.update {
                            it.copy(
                                isMonitoring = true,
                                isRecording = true,
                                errorMessage = null
                            )
                        }
                    }
                    is RecordingState.Completed -> {
                        _uiState.update {
                            it.copy(
                                isMonitoring = true,
                                isRecording = false,
                                lastRecordedAudio = state.audioData,
                                errorMessage = null
                            )
                        }
                    }
                    is RecordingState.Error -> {
                        _uiState.update {
                            it.copy(
                                isRecording = false,
                                errorMessage = state.message
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            audioRecorder.durationMs.collect { duration ->
                _uiState.update { it.copy(durationMs = duration) }
            }
        }
    }

    /**
     * 音声モニタリングを開始
     *
     * 音量を監視し、閾値を超えたら自動的に録音を開始する。
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startMonitoring() {
        audioRecorder.startMonitoring()
    }

    /**
     * 音声モニタリングを停止
     */
    fun stopMonitoring() {
        audioRecorder.stopMonitoring()
    }

    /**
     * 最後に録音されたAudioDataをクリア
     */
    fun clearLastRecording() {
        _uiState.update { it.copy(lastRecordedAudio = null) }
    }

    /**
     * エラーをクリア
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
    }
}
