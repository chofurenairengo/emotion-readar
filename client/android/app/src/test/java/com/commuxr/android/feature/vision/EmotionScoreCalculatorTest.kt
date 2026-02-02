package com.commuxr.android.feature.vision

import com.google.mediapipe.tasks.components.containers.Category
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionScoreCalculatorTest {

    @Test
    fun `calculate returns EmotionScores with all values in range 0 to 1`() {
        val result = EmotionScoreCalculator.calculate(emptyList())

        assertTrue("happy should be >= 0", result.happy >= 0f)
        assertTrue("happy should be <= 1", result.happy <= 1f)
        assertTrue("sad should be >= 0", result.sad >= 0f)
        assertTrue("sad should be <= 1", result.sad <= 1f)
        assertTrue("angry should be >= 0", result.angry >= 0f)
        assertTrue("angry should be <= 1", result.angry <= 1f)
        assertTrue("confused should be >= 0", result.confused >= 0f)
        assertTrue("confused should be <= 1", result.confused <= 1f)
        assertTrue("surprised should be >= 0", result.surprised >= 0f)
        assertTrue("surprised should be <= 1", result.surprised <= 1f)
        assertTrue("neutral should be >= 0", result.neutral >= 0f)
        assertTrue("neutral should be <= 1", result.neutral <= 1f)
        assertTrue("fearful should be >= 0", result.fearful >= 0f)
        assertTrue("fearful should be <= 1", result.fearful <= 1f)
        assertTrue("disgusted should be >= 0", result.disgusted >= 0f)
        assertTrue("disgusted should be <= 1", result.disgusted <= 1f)
    }

    @Test
    fun `empty blendshapes returns high neutral score`() {
        val result = EmotionScoreCalculator.calculate(emptyList())

        assertEquals(1.0f, result.neutral, 0.001f)
    }

    @Test
    fun `high smile scores result in high happy emotion`() {
        val blendshapes = listOf(
            createCategory("mouthSmileLeft", 0.9f),
            createCategory("mouthSmileRight", 0.9f),
            createCategory("cheekSquintLeft", 0.8f),
            createCategory("cheekSquintRight", 0.8f)
        )

        val result = EmotionScoreCalculator.calculate(blendshapes)

        assertTrue("Happy score should be > 0.7", result.happy > 0.7f)
        assertTrue("Neutral score should be low", result.neutral < 0.3f)
    }

    @Test
    fun `high frown scores result in high sad emotion`() {
        val blendshapes = listOf(
            createCategory("browInnerUp", 0.8f),
            createCategory("mouthFrownLeft", 0.9f),
            createCategory("mouthFrownRight", 0.9f)
        )

        val result = EmotionScoreCalculator.calculate(blendshapes)

        assertTrue("Sad score should be > 0.7", result.sad > 0.7f)
    }

    @Test
    fun `high brow down and jaw forward result in high angry emotion`() {
        val blendshapes = listOf(
            createCategory("browDownLeft", 0.8f),
            createCategory("browDownRight", 0.8f),
            createCategory("jawForward", 0.7f)
        )

        val result = EmotionScoreCalculator.calculate(blendshapes)

        assertTrue("Angry score should be > 0.6", result.angry > 0.6f)
    }

    @Test
    fun `mixed brow movements result in confused emotion`() {
        val blendshapes = listOf(
            createCategory("browInnerUp", 0.7f),
            createCategory("browDownLeft", 0.6f),
            createCategory("browDownRight", 0.6f)
        )

        val result = EmotionScoreCalculator.calculate(blendshapes)

        assertTrue("Confused score should be > 0.3", result.confused > 0.3f)
    }

    @Test
    fun `wide eyes and open jaw result in high surprised emotion`() {
        val blendshapes = listOf(
            createCategory("eyeWideLeft", 0.9f),
            createCategory("eyeWideRight", 0.9f),
            createCategory("browInnerUp", 0.8f),
            createCategory("jawOpen", 0.8f)
        )

        val result = EmotionScoreCalculator.calculate(blendshapes)

        assertTrue("Surprised score should be > 0.7", result.surprised > 0.7f)
    }

    @Test
    fun `wide eyes and raised brows result in fearful emotion`() {
        val blendshapes = listOf(
            createCategory("eyeWideLeft", 0.8f),
            createCategory("eyeWideRight", 0.8f),
            createCategory("browInnerUp", 0.7f)
        )

        val result = EmotionScoreCalculator.calculate(blendshapes)

        assertTrue("Fearful score should be > 0.6", result.fearful > 0.6f)
    }

    @Test
    fun `nose sneer and upper lip raise result in disgusted emotion`() {
        val blendshapes = listOf(
            createCategory("noseSneerLeft", 0.8f),
            createCategory("noseSneerRight", 0.8f),
            createCategory("mouthUpperUpLeft", 0.7f),
            createCategory("mouthUpperUpRight", 0.7f)
        )

        val result = EmotionScoreCalculator.calculate(blendshapes)

        assertTrue("Disgusted score should be > 0.6", result.disgusted > 0.6f)
    }

    @Test
    fun `neutral is calculated based on max emotion`() {
        val blendshapes = listOf(
            createCategory("mouthSmileLeft", 0.6f),
            createCategory("mouthSmileRight", 0.6f)
        )

        val result = EmotionScoreCalculator.calculate(blendshapes)

        // happy が約0.4以上の場合、neutral は約0.6以下になるはず
        assertTrue("Neutral should be approximately 1.0 - max emotion", result.neutral < 1.0f - result.happy + 0.1f)
    }

    @Test
    fun `toMap returns all 8 emotion keys`() {
        val result = EmotionScoreCalculator.calculate(emptyList())
        val map = result.toMap()

        assertEquals(8, map.size)
        assertTrue(map.containsKey("happy"))
        assertTrue(map.containsKey("sad"))
        assertTrue(map.containsKey("angry"))
        assertTrue(map.containsKey("confused"))
        assertTrue(map.containsKey("surprised"))
        assertTrue(map.containsKey("neutral"))
        assertTrue(map.containsKey("fearful"))
        assertTrue(map.containsKey("disgusted"))
    }

    /**
     * テスト用のCategoryモックを作成
     */
    private fun createCategory(name: String, score: Float): Category {
        return mockk<Category> {
            every { categoryName() } returns name
            every { score() } returns score
        }
    }
}
