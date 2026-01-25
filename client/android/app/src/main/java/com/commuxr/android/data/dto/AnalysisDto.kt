package com.commuxr.android.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * ANALYSIS_REQUEST メッセージ
 */
@JsonClass(generateAdapter = true)
data class AnalysisRequest(
    val type: String = "ANALYSIS_REQUEST",
    @Json(name = "session_id") val sessionId: String,
    val timestamp: String,  // ISO 8601
    @Json(name = "emotion_scores") val emotionScores: Map<String, Float>,
    @Json(name = "audio_data") val audioData: String?,  // Base64
    @Json(name = "audio_format") val audioFormat: String?  // "wav" | "opus" | "pcm"
)

/**
 * ANALYSIS_RESPONSE メッセージ（サーバーからの応答）
 */
@JsonClass(generateAdapter = true)
data class AnalysisResponse(
    val type: String,
    val timestamp: String,
    val emotion: EmotionInterpretation,
    val transcription: TranscriptionResult?,
    val suggestions: List<ResponseSuggestion>,
    @Json(name = "situation_analysis") val situationAnalysis: String,
    @Json(name = "processing_time_ms") val processingTimeMs: Int
)

/**
 * 感情解釈結果
 */
@JsonClass(generateAdapter = true)
data class EmotionInterpretation(
    @Json(name = "primary_emotion") val primaryEmotion: String,
    val intensity: String,  // "low" | "medium" | "high"
    val description: String,
    val suggestion: String?
)

/**
 * 音声認識結果
 */
@JsonClass(generateAdapter = true)
data class TranscriptionResult(
    val text: String,
    val confidence: Float,
    val language: String,
    @Json(name = "duration_ms") val durationMs: Int
)

/**
 * 応答候補
 */
@JsonClass(generateAdapter = true)
data class ResponseSuggestion(
    val text: String,
    val intent: String
)
