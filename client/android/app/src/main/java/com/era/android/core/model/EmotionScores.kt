package com.era.android.core.model

data class EmotionScores(
    val happy: Float = 0f,
    val sad: Float = 0f,
    val angry: Float = 0f,
    val confused: Float = 0f,
    val surprised: Float = 0f,
    val neutral: Float = 0f,
    val fearful: Float = 0f,
    val disgusted: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Float> = mapOf(
        "happy" to happy,
        "sad" to sad,
        "angry" to angry,
        "confused" to confused,
        "surprised" to surprised,
        "neutral" to neutral,
        "fearful" to fearful,
        "disgusted" to disgusted
    )
}
