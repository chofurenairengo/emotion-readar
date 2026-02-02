package com.commuxr.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commuxr.android.domain.usecase.EndSessionUseCase
import com.commuxr.android.domain.usecase.SendAnalysisUseCase
import com.commuxr.android.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * メイン画面のViewModel（簡略化版）
 *
 * UIはUnity側で管理するため、セッション管理とデータ送信のみを担当する。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val startSessionUseCase: StartSessionUseCase,
    private val endSessionUseCase: EndSessionUseCase,
    private val sendAnalysisUseCase: SendAnalysisUseCase
) : ViewModel() {

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * セッションを開始
     */
    fun startSession() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true

            startSessionUseCase()
                .onSuccess {
                    _isSessionActive.value = true
                }
                .onFailure {
                    // エラーはUnity側でハンドリング
                }

            _isLoading.value = false
        }
    }

    /**
     * セッションを終了
     */
    fun endSession() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true

            endSessionUseCase()
                .onSuccess {
                    _isSessionActive.value = false
                }
                .onFailure {
                    // エラーはUnity側でハンドリング
                }

            _isLoading.value = false
        }
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
        // ViewModel破棄時にセッションが残っていれば終了
        if (_isSessionActive.value) {
            viewModelScope.launch {
                endSessionUseCase()
            }
        }
    }
}
