package com.era.android.feature.vision

import com.google.mediapipe.tasks.components.containers.Category

/**
 * MediaPipe Blendshapeから8種類の感情スコアを算出するクラス
 *
 * 算出する感情:
 * - happy: 幸せ
 * - sad: 悲しみ
 * - angry: 怒り
 * - confused: 困惑
 * - surprised: 驚き
 * - neutral: 中立
 * - fearful: 恐れ
 * - disgusted: 嫌悪
 */
object EmotionScoreCalculator {

    /**
     * Blendshapeから感情スコアを算出
     *
     * @param blendshapes MediaPipeから取得したBlendshapeリスト
     * @return 8種類の感情スコア(0.0~1.0)
     */
    fun calculate(blendshapes: List<Category>): EmotionScores {
        val blendshapeMap = blendshapes.associate { it.categoryName() to it.score() }

        val happy = calculateHappy(blendshapeMap)
        val sad = calculateSad(blendshapeMap)
        val angry = calculateAngry(blendshapeMap)
        val confused = calculateConfused(blendshapeMap)
        val surprised = calculateSurprised(blendshapeMap)
        val fearful = calculateFearful(blendshapeMap)
        val disgusted = calculateDisgusted(blendshapeMap)

        // neutral: 他の感情が低い時に高くなる（最大感情値ベース）
        val maxEmotion = maxOf(happy, sad, angry, confused, surprised, fearful, disgusted)
        val neutral = (1.0f - maxEmotion).coerceIn(0f, 1f)

        return EmotionScores(
            happy = happy,
            sad = sad,
            angry = angry,
            confused = confused,
            surprised = surprised,
            neutral = neutral,
            fearful = fearful,
            disgusted = disgusted,
        )
    }

    /**
     * 幸せ (happy) スコアを算出
     * 主要Blendshape: mouthSmileLeft/Right, cheekSquint
     */
    private fun calculateHappy(blendshapeMap: Map<String, Float>): Float {
        val mouthSmileLeft = blendshapeMap["mouthSmileLeft"] ?: 0f
        val mouthSmileRight = blendshapeMap["mouthSmileRight"] ?: 0f
        val cheekSquintLeft = blendshapeMap["cheekSquintLeft"] ?: 0f
        val cheekSquintRight = blendshapeMap["cheekSquintRight"] ?: 0f

        return ((mouthSmileLeft + mouthSmileRight) / 2f * 0.7f +
                (cheekSquintLeft + cheekSquintRight) / 2f * 0.3f).coerceIn(0f, 1f)
    }

    /**
     * 悲しみ (sad) スコアを算出
     * 主要Blendshape: browInnerUp, mouthFrownLeft/Right
     */
    private fun calculateSad(blendshapeMap: Map<String, Float>): Float {
        val browInnerUp = blendshapeMap["browInnerUp"] ?: 0f
        val mouthFrownLeft = blendshapeMap["mouthFrownLeft"] ?: 0f
        val mouthFrownRight = blendshapeMap["mouthFrownRight"] ?: 0f

        return (browInnerUp * 0.4f +
                (mouthFrownLeft + mouthFrownRight) / 2f * 0.6f).coerceIn(0f, 1f)
    }

    /**
     * 怒り (angry) スコアを算出
     * 主要Blendshape: browDownLeft/Right, jawForward
     */
    private fun calculateAngry(blendshapeMap: Map<String, Float>): Float {
        val browDownLeft = blendshapeMap["browDownLeft"] ?: 0f
        val browDownRight = blendshapeMap["browDownRight"] ?: 0f
        val jawForward = blendshapeMap["jawForward"] ?: 0f

        return ((browDownLeft + browDownRight) / 2f * 0.6f +
                jawForward * 0.4f).coerceIn(0f, 1f)
    }

    /**
     * 困惑 (confused) スコアを算出
     * 主要Blendshape: browInnerUp + browDown混合
     */
    private fun calculateConfused(blendshapeMap: Map<String, Float>): Float {
        val browInnerUp = blendshapeMap["browInnerUp"] ?: 0f
        val browDownLeft = blendshapeMap["browDownLeft"] ?: 0f
        val browDownRight = blendshapeMap["browDownRight"] ?: 0f

        // 眉が上がりながら下がる矛盾した動きを検出
        val browDown = (browDownLeft + browDownRight) / 2f
        return (browInnerUp * browDown * 2f).coerceIn(0f, 1f)
    }

    /**
     * 驚き (surprised) スコアを算出
     * 主要Blendshape: eyeWideLeft/Right, browInnerUp, jawOpen
     */
    private fun calculateSurprised(blendshapeMap: Map<String, Float>): Float {
        val eyeWideLeft = blendshapeMap["eyeWideLeft"] ?: 0f
        val eyeWideRight = blendshapeMap["eyeWideRight"] ?: 0f
        val browInnerUp = blendshapeMap["browInnerUp"] ?: 0f
        val jawOpen = blendshapeMap["jawOpen"] ?: 0f

        return ((eyeWideLeft + eyeWideRight) / 2f * 0.4f +
                browInnerUp * 0.3f +
                jawOpen * 0.3f).coerceIn(0f, 1f)
    }

    /**
     * 恐れ (fearful) スコアを算出
     * 主要Blendshape: eyeWideLeft/Right, browInnerUp
     */
    private fun calculateFearful(blendshapeMap: Map<String, Float>): Float {
        val eyeWideLeft = blendshapeMap["eyeWideLeft"] ?: 0f
        val eyeWideRight = blendshapeMap["eyeWideRight"] ?: 0f
        val browInnerUp = blendshapeMap["browInnerUp"] ?: 0f

        return ((eyeWideLeft + eyeWideRight) / 2f * 0.5f +
                browInnerUp * 0.5f).coerceIn(0f, 1f)
    }

    /**
     * 嫌悪 (disgusted) スコアを算出
     * 主要Blendshape: noseSneerLeft/Right, upperLipRaiser
     */
    private fun calculateDisgusted(blendshapeMap: Map<String, Float>): Float {
        val noseSneerLeft = blendshapeMap["noseSneerLeft"] ?: 0f
        val noseSneerRight = blendshapeMap["noseSneerRight"] ?: 0f
        val upperLipRaiserLeft = blendshapeMap["mouthUpperUpLeft"] ?: 0f
        val upperLipRaiserRight = blendshapeMap["mouthUpperUpRight"] ?: 0f

        return ((noseSneerLeft + noseSneerRight) / 2f * 0.6f +
                (upperLipRaiserLeft + upperLipRaiserRight) / 2f * 0.4f).coerceIn(0f, 1f)
    }
}
