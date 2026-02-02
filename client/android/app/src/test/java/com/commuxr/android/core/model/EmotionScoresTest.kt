package com.commuxr.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionScoresTest {

    @Test
    fun `default values are all zero`() {
        val scores = EmotionScores()
        assertEquals(0f, scores.happy)
        assertEquals(0f, scores.sad)
        assertEquals(0f, scores.angry)
        assertEquals(0f, scores.confused)
        assertEquals(0f, scores.surprised)
        assertEquals(0f, scores.neutral)
        assertEquals(0f, scores.fearful)
        assertEquals(0f, scores.disgusted)
    }

    @Test
    fun `timestamp is set automatically`() {
        val before = System.currentTimeMillis()
        val scores = EmotionScores()
        val after = System.currentTimeMillis()
        assertTrue(scores.timestamp in before..after)
    }

    @Test
    fun `toMap returns all 8 emotion scores`() {
        val scores = EmotionScores(
            happy = 0.9f,
            sad = 0.1f,
            angry = 0.2f,
            confused = 0.3f,
            surprised = 0.4f,
            neutral = 0.5f,
            fearful = 0.6f,
            disgusted = 0.7f
        )
        val map = scores.toMap()
        assertEquals(8, map.size)
        assertEquals(0.9f, map["happy"])
        assertEquals(0.1f, map["sad"])
        assertEquals(0.2f, map["angry"])
        assertEquals(0.3f, map["confused"])
        assertEquals(0.4f, map["surprised"])
        assertEquals(0.5f, map["neutral"])
        assertEquals(0.6f, map["fearful"])
        assertEquals(0.7f, map["disgusted"])
    }

    @Test
    fun `toMap does not include timestamp`() {
        val scores = EmotionScores()
        val map = scores.toMap()
        assertTrue("timestamp" !in map)
    }

    @Test
    fun `data class equality works correctly`() {
        val timestamp = 1000L
        val scores1 = EmotionScores(happy = 0.5f, timestamp = timestamp)
        val scores2 = EmotionScores(happy = 0.5f, timestamp = timestamp)
        assertEquals(scores1, scores2)
    }

    @Test
    fun `copy creates new instance with updated values`() {
        val original = EmotionScores(happy = 0.5f)
        val updated = original.copy(happy = 0.9f)
        assertEquals(0.5f, original.happy)
        assertEquals(0.9f, updated.happy)
    }
}
