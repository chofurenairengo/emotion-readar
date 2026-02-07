package com.commuxr.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commuxr.android.data.websocket.WebSocketClient
import com.commuxr.android.domain.usecase.SendAnalysisUseCase
import com.commuxr.android.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * メイン画面のViewModel
 *
 * アプリ起動時にセッションを自動開始し、セッション管理とデータ送信を担当する。
 * セッション終了はサーバー側がWebSocket切断を検知して自動的に行う。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val startSessionUseCase: StartSessionUseCase,
    private val sendAnalysisUseCase: SendAnalysisUseCase,
    private val webSocketClient: WebSocketClient
) : ViewModel() {

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val BASE_RETRY_DELAY_MS = 1_000L
        // TODO: Firebase Auth 統合後に実際のトークン取得に置き換える
        private const val DEV_TOKEN = "dev-token"
    }

    init {
        viewModelScope.launch {
            startSessionWithRetry()
        }
    }

    private suspend fun startSessionWithRetry() {
        var attempt = 0
        while (attempt < MAX_RETRY_ATTEMPTS) {
            startSessionUseCase(DEV_TOKEN)
                .onSuccess {
                    _isSessionActive.value = true
                    return
                }

            attempt++
            if (attempt < MAX_RETRY_ATTEMPTS) {
                val delayMs = BASE_RETRY_DELAY_MS * (1 shl (attempt - 1).coerceAtMost(5))
                Log.w(TAG, "Session start failed, retrying in ${delayMs}ms (attempt $attempt/$MAX_RETRY_ATTEMPTS)")
                delay(delayMs)
            }
        }
        Log.e(TAG, "Session start failed after $MAX_RETRY_ATTEMPTS attempts")
    }

    /**
     * 感情スコアが更新されたときに呼び出す
     *
     * セッションがアクティブな場合、自動的に解析データを送信する
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
        if (!_isSessionActive.value) return

        // 自動送信
        sendAnalysisUseCase(emotionScores, audioData, audioFormat)
    }

    override fun onCleared() {
        super.onCleared()
        // WS切断のみ。サーバーが切断を検知してセッションを終了する
        webSocketClient.disconnect()
    }
}
