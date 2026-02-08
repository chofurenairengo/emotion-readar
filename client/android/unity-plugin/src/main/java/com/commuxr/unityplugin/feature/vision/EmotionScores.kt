package com.commuxr.unityplugin.feature.vision

/**
 * 8種類の感情スコアを保持するデータクラス
 * 各スコアは0.0〜1.0の範囲
 */
data class EmotionScores(
    val happy: Float,
    val sad: Float,
    val angry: Float,
    val confused: Float,
    val surprised: Float,
    val neutral: Float,
    val fearful: Float,
    val disgusted: Float,
) {
    init {
        require(happy in 0f..1f) { "happy must be in range 0.0..1.0" }
        require(sad in 0f..1f) { "sad must be in range 0.0..1.0" }
        require(angry in 0f..1f) { "angry must be in range 0.0..1.0" }
        require(confused in 0f..1f) { "confused must be in range 0.0..1.0" }
        require(surprised in 0f..1f) { "surprised must be in range 0.0..1.0" }
        require(neutral in 0f..1f) { "neutral must be in range 0.0..1.0" }
        require(fearful in 0f..1f) { "fearful must be in range 0.0..1.0" }
        require(disgusted in 0f..1f) { "disgusted must be in range 0.0..1.0" }
    }

    /**
     * JSONシリアライズ用にMapに変換
     */
    fun toMap(): Map<String, Float> = mapOf(
        "happy" to happy,
        "sad" to sad,
        "angry" to angry,
        "confused" to confused,
        "surprised" to surprised,
        "neutral" to neutral,
        "fearful" to fearful,
        "disgusted" to disgusted,
    )
}
