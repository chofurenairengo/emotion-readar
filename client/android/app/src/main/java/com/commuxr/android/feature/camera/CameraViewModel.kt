package com.commuxr.android.feature.camera

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * カメラ状態の管理を担当するViewModel
 */
class CameraViewModel : ViewModel() {

    /**
     * カメラのUI状態
     *
     * @param isCameraReady カメラが準備完了かどうか
     * @param errorMessage エラーメッセージ（nullの場合はエラーなし）
     */
    data class UiState(
        val isCameraReady: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * カメラの準備が完了したことを通知する
     */
    fun onCameraReady() {
        _uiState.update { it.copy(isCameraReady = true, errorMessage = null) }
    }

    /**
     * エラーが発生したことを通知する
     *
     * @param message エラーメッセージ
     */
    fun onError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    /**
     * エラーをクリアする
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
