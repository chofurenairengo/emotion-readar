package com.era.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.era.android.data.websocket.WebSocketClient
import com.era.android.domain.usecase.EndSessionUseCase
import com.era.android.domain.usecase.SendAnalysisUseCase
import com.era.android.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * メイン画面のViewModel
 *
 * セッション管理とWebSocket接続状態を管理する
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val startSessionUseCase: StartSessionUseCase,
    private val endSessionUseCase: EndSessionUseCase,
    private val sendAnalysisUseCase: SendAnalysisUseCase,
    private val webSocketClient: WebSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // WebSocket接続状態を監視
        viewModelScope.launch {
            webSocketClient.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    /**
     * セッションを開始
     */
    fun startSession() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            startSessionUseCase()
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            session = session,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "Failed to start session"
                        )
                    }
                }
        }
    }

    /**
     * セッションを終了
     */
    fun endSession() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            endSessionUseCase()
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            session = session,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = exception.message ?: "Failed to end session"
                        )
                    }
                }
        }
    }

    /**
     * 感情スコアが更新されたときに呼び出す
     *
     * セッションがアクティブで接続中の場合、自動的に解析データを送信する
     *
     * @param emotionScores 感情スコアマップ
     * @param audioData Base64エンコードされた音声データ（オプション）
     * @param audioFormat 音声フォーマット（オプション）
     */
    fun onEmotionScoresUpdated(
        emotionScores: Map<String, Float>,
        audioData: String? = null,
        audioFormat: String? = null
    ) {
        // セッションがアクティブでなければ送信しない
        if (!_uiState.value.isSessionActive) return

        // 自動送信
        sendAnalysisUseCase(emotionScores, audioData, audioFormat)
    }

    /**
     * エラーをクリア
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel破棄時にセッションが残っていれば終了
        if (_uiState.value.isSessionActive) {
            viewModelScope.launch {
                endSessionUseCase()
            }
        }
    }
}
