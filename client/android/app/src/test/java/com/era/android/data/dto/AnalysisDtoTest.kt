package com.era.android.data.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Analysis関連DTOのユニットテスト
 */
class AnalysisDtoTest {

    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    // ========== AnalysisRequest Tests ==========

    @Test
    fun `AnalysisRequest - フル情報でJSON変換できる`() {
        val request = AnalysisRequest(
            sessionId = "session-123",
            timestamp = "2024-01-01T12:00:00Z",
            emotionScores = mapOf(
                "happy" to 0.8f,
                "sad" to 0.1f,
                "angry" to 0.05f,
                "confused" to 0.02f,
                "surprised" to 0.01f,
                "neutral" to 0.01f,
                "fearful" to 0.005f,
                "disgusted" to 0.005f
            ),
            audioData = "SGVsbG8gV29ybGQ=",
            audioFormat = "wav"
        )

        val adapter = moshi.adapter(AnalysisRequest::class.java)
        val json = adapter.toJson(request)

        // JSON構造の確認
        assertTrue(json.contains("\"type\":\"ANALYSIS_REQUEST\""))
        assertTrue(json.contains("\"session_id\":\"session-123\""))
        assertTrue(json.contains("\"timestamp\":\"2024-01-01T12:00:00Z\""))
        assertTrue(json.contains("\"emotion_scores\":{"))
        assertTrue(json.contains("\"audio_data\":\"SGVsbG8gV29ybGQ=\""))
        assertTrue(json.contains("\"audio_format\":\"wav\""))

        // パース確認
        val parsed = adapter.fromJson(json)
        assertNotNull(parsed)
        assertEquals("ANALYSIS_REQUEST", parsed?.type)
        assertEquals("session-123", parsed?.sessionId)
        assertEquals(8, parsed?.emotionScores?.size)
        assertEquals(0.8f, parsed?.emotionScores?.get("happy"))
        assertEquals("SGVsbG8gV29ybGQ=", parsed?.audioData)
        assertEquals("wav", parsed?.audioFormat)
    }

    @Test
    fun `AnalysisRequest - 音声データなしでJSON変換できる`() {
        val request = AnalysisRequest(
            sessionId = "session-456",
            timestamp = "2024-01-01T13:00:00Z",
            emotionScores = mapOf("neutral" to 1.0f),
            audioData = null,
            audioFormat = null
        )

        val adapter = moshi.adapter(AnalysisRequest::class.java)
        val json = adapter.toJson(request)

        assertTrue(json.contains("\"audio_data\":null"))
        assertTrue(json.contains("\"audio_format\":null"))

        val parsed = adapter.fromJson(json)
        assertNull(parsed?.audioData)
        assertNull(parsed?.audioFormat)
    }

    @Test
    fun `AnalysisRequest - 各種音声フォーマットをサポート`() {
        val formats = listOf("wav", "opus", "pcm")

        formats.forEach { format ->
            val request = AnalysisRequest(
                sessionId = "session-test",
                timestamp = "2024-01-01T14:00:00Z",
                emotionScores = mapOf("happy" to 0.5f),
                audioData = "data",
                audioFormat = format
            )

            val adapter = moshi.adapter(AnalysisRequest::class.java)
            val json = adapter.toJson(request)
            val parsed = adapter.fromJson(json)

            assertEquals(format, parsed?.audioFormat)
        }
    }

    // ========== AnalysisResponse Tests ==========

    @Test
    fun `AnalysisResponse - 完全なレスポンスをパースできる`() {
        val json = """
        {
            "type": "ANALYSIS_RESPONSE",
            "timestamp": "2024-01-01T12:00:00Z",
            "emotion": {
                "primary_emotion": "happy",
                "intensity": "high",
                "description": "相手は非常に喜んでいます",
                "suggestion": "ポジティブな話題を続けましょう"
            },
            "transcription": {
                "text": "今日はとても良い天気ですね",
                "confidence": 0.98,
                "language": "ja",
                "duration_ms": 3000
            },
            "suggestions": [
                {
                    "text": "本当にそうですね",
                    "intent": "agreement"
                },
                {
                    "text": "散歩に行きませんか？",
                    "intent": "proposal"
                }
            ],
            "situation_analysis": "良好な会話が続いています",
            "processing_time_ms": 450
        }
        """.trimIndent()

        val adapter = moshi.adapter(AnalysisResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("ANALYSIS_RESPONSE", response?.type)
        assertEquals("2024-01-01T12:00:00Z", response?.timestamp)

        // Emotion
        assertEquals("happy", response?.emotion?.primaryEmotion)
        assertEquals("high", response?.emotion?.intensity)
        assertEquals("相手は非常に喜んでいます", response?.emotion?.description)
        assertEquals("ポジティブな話題を続けましょう", response?.emotion?.suggestion)

        // Transcription
        assertEquals("今日はとても良い天気ですね", response?.transcription?.text)
        assertEquals(0.98f, response?.transcription?.confidence)
        assertEquals("ja", response?.transcription?.language)
        assertEquals(3000, response?.transcription?.durationMs)

        // Suggestions
        assertEquals(2, response?.suggestions?.size)
        assertEquals("本当にそうですね", response?.suggestions?.get(0)?.text)
        assertEquals("agreement", response?.suggestions?.get(0)?.intent)

        // Meta
        assertEquals("良好な会話が続いています", response?.situationAnalysis)
        assertEquals(450, response?.processingTimeMs)
    }

    @Test
    fun `AnalysisResponse - transcriptionがnullでもパースできる`() {
        val json = """
        {
            "type": "ANALYSIS_RESPONSE",
            "timestamp": "2024-01-01T12:00:00Z",
            "emotion": {
                "primary_emotion": "neutral",
                "intensity": "low",
                "description": "無表情",
                "suggestion": null
            },
            "transcription": null,
            "suggestions": [],
            "situation_analysis": "音声なし",
            "processing_time_ms": 100
        }
        """.trimIndent()

        val adapter = moshi.adapter(AnalysisResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertNull(response?.transcription)
        assertNull(response?.emotion?.suggestion)
        assertTrue(response?.suggestions?.isEmpty() == true)
    }

    @Test
    fun `AnalysisResponse - 各種感情の強度をサポート`() {
        val intensities = listOf("low", "medium", "high")

        intensities.forEach { intensity ->
            val json = """
            {
                "type": "ANALYSIS_RESPONSE",
                "timestamp": "2024-01-01T12:00:00Z",
                "emotion": {
                    "primary_emotion": "happy",
                    "intensity": "$intensity",
                    "description": "Test",
                    "suggestion": null
                },
                "transcription": null,
                "suggestions": [],
                "situation_analysis": "Test",
                "processing_time_ms": 100
            }
            """.trimIndent()

            val adapter = moshi.adapter(AnalysisResponse::class.java)
            val response = adapter.fromJson(json)

            assertEquals(intensity, response?.emotion?.intensity)
        }
    }

    // ========== EmotionInterpretation Tests ==========

    @Test
    fun `EmotionInterpretation - 全フィールドあり`() {
        val json = """
        {
            "primary_emotion": "sad",
            "intensity": "medium",
            "description": "相手は少し悲しそうです",
            "suggestion": "励ましの言葉をかけましょう"
        }
        """.trimIndent()

        val adapter = moshi.adapter(EmotionInterpretation::class.java)
        val emotion = adapter.fromJson(json)

        assertNotNull(emotion)
        assertEquals("sad", emotion?.primaryEmotion)
        assertEquals("medium", emotion?.intensity)
        assertEquals("相手は少し悲しそうです", emotion?.description)
        assertEquals("励ましの言葉をかけましょう", emotion?.suggestion)
    }

    @Test
    fun `EmotionInterpretation - suggestionがnull`() {
        val json = """
        {
            "primary_emotion": "neutral",
            "intensity": "low",
            "description": "特に感情は見られません",
            "suggestion": null
        }
        """.trimIndent()

        val adapter = moshi.adapter(EmotionInterpretation::class.java)
        val emotion = adapter.fromJson(json)

        assertNull(emotion?.suggestion)
    }

    // ========== TranscriptionResult Tests ==========

    @Test
    fun `TranscriptionResult - 日本語音声認識`() {
        val json = """
        {
            "text": "おはようございます",
            "confidence": 0.99,
            "language": "ja",
            "duration_ms": 1500
        }
        """.trimIndent()

        val adapter = moshi.adapter(TranscriptionResult::class.java)
        val transcription = adapter.fromJson(json)

        assertNotNull(transcription)
        assertEquals("おはようございます", transcription?.text)
        assertEquals(0.99f, transcription?.confidence)
        assertEquals("ja", transcription?.language)
        assertEquals(1500, transcription?.durationMs)
    }

    @Test
    fun `TranscriptionResult - 英語音声認識`() {
        val json = """
        {
            "text": "Hello, how are you?",
            "confidence": 0.95,
            "language": "en",
            "duration_ms": 2000
        }
        """.trimIndent()

        val adapter = moshi.adapter(TranscriptionResult::class.java)
        val transcription = adapter.fromJson(json)

        assertEquals("en", transcription?.language)
        assertEquals("Hello, how are you?", transcription?.text)
    }

    @Test
    fun `TranscriptionResult - 低信頼度`() {
        val json = """
        {
            "text": "...",
            "confidence": 0.3,
            "language": "ja",
            "duration_ms": 500
        }
        """.trimIndent()

        val adapter = moshi.adapter(TranscriptionResult::class.java)
        val transcription = adapter.fromJson(json)

        assertEquals(0.3f, transcription?.confidence)
    }

    // ========== ResponseSuggestion Tests ==========

    @Test
    fun `ResponseSuggestion - 各種intentをサポート`() {
        val intents = listOf(
            "agreement" to "そうですね",
            "empathy" to "それは大変でしたね",
            "proposal" to "こうしてみませんか？",
            "question" to "どうしてですか？"
        )

        intents.forEach { (intent, text) ->
            val json = """
            {
                "text": "$text",
                "intent": "$intent"
            }
            """.trimIndent()

            val adapter = moshi.adapter(ResponseSuggestion::class.java)
            val suggestion = adapter.fromJson(json)

            assertEquals(intent, suggestion?.intent)
            assertEquals(text, suggestion?.text)
        }
    }

    // ========== Edge Cases ==========

    @Test
    fun `AnalysisRequest - 空のemotionScoresマップ`() {
        val request = AnalysisRequest(
            sessionId = "session-empty",
            timestamp = "2024-01-01T12:00:00Z",
            emotionScores = emptyMap(),
            audioData = null,
            audioFormat = null
        )

        val adapter = moshi.adapter(AnalysisRequest::class.java)
        val json = adapter.toJson(request)
        val parsed = adapter.fromJson(json)

        assertEquals(0, parsed?.emotionScores?.size)
    }

    @Test
    fun `AnalysisResponse - 空のsuggestionsリスト`() {
        val json = """
        {
            "type": "ANALYSIS_RESPONSE",
            "timestamp": "2024-01-01T12:00:00Z",
            "emotion": {
                "primary_emotion": "neutral",
                "intensity": "low",
                "description": "特になし",
                "suggestion": null
            },
            "transcription": null,
            "suggestions": [],
            "situation_analysis": "沈黙",
            "processing_time_ms": 50
        }
        """.trimIndent()

        val adapter = moshi.adapter(AnalysisResponse::class.java)
        val response = adapter.fromJson(json)

        assertTrue(response?.suggestions?.isEmpty() == true)
    }

    @Test
    fun `TranscriptionResult - 非常に長いテキスト`() {
        val longText = "a".repeat(1000)
        val json = """
        {
            "text": "$longText",
            "confidence": 0.9,
            "language": "en",
            "duration_ms": 30000
        }
        """.trimIndent()

        val adapter = moshi.adapter(TranscriptionResult::class.java)
        val transcription = adapter.fromJson(json)

        assertEquals(longText, transcription?.text)
        assertEquals(30000, transcription?.durationMs)
    }
}
