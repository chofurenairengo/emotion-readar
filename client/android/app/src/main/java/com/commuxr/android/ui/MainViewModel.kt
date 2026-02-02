package com.commuxr.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commuxr.android.core.model.EmotionScores
import com.commuxr.android.data.websocket.WebSocketClient
import com.commuxr.android.feature.vision.EmotionScoreCalculator
import com.commuxr.android.vision.FaceLandmarkerHelper
import com.google.mediapipe.tasks.components.containers.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val webSocketClient: WebSocketClient
) : ViewModel(), FaceLandmarkerHelper.Listener {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            webSocketClient.analysisResponses.collect { response ->
                val suggestionTexts = response.suggestions.take(2).map { it.text }
                _uiState.update { it.copy(suggestions = suggestionTexts) }
            }
        }
    }

    override fun onResults(blendshapes: List<Category>, timestampMs: Long) {
        val emotionMap = EmotionScoreCalculator.calculate(blendshapes)
        val emotions = EmotionScores(
            happy = emotionMap["happy"] ?: 0f,
            sad = emotionMap["sad"] ?: 0f,
            angry = emotionMap["angry"] ?: 0f,
            confused = emotionMap["confused"] ?: 0f,
            surprised = emotionMap["surprised"] ?: 0f,
            neutral = emotionMap["neutral"] ?: 0f,
            fearful = emotionMap["fearful"] ?: 0f,
            disgusted = emotionMap["disgusted"] ?: 0f,
            timestamp = timestampMs
        )
        _uiState.update { it.copy(currentEmotions = emotions) }
    }

    override fun onNoFaceDetected(timestampMs: Long) {
        // 顔未検出時は感情アイコンを前回の状態で維持
    }

    override fun onError(error: String) {
        _uiState.update { it.copy(errorMessage = error) }
    }

    fun onCameraReady() {
        _uiState.update { it.copy(isCameraReady = true, errorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
