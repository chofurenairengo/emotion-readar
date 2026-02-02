package com.commuxr.android.ui

import com.commuxr.android.core.model.EmotionScores

data class MainUiState(
    val currentEmotions: EmotionScores? = null,
    val suggestions: List<String> = emptyList(),
    val isCameraReady: Boolean = false,
    val errorMessage: String? = null
)
