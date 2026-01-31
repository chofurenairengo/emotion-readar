package com.era.android.data.websocket

import com.era.android.data.dto.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * WebSocketClientのユニットテスト
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketClientTest {

    private lateinit var client: WebSocketClient
    private lateinit var testScope: TestScope
    private val testDispatcher = StandardTestDispatcher()

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        // Note: WebSocketClientは実際のOkHttpClientを使用するため、
        // このテストでは接続状態の管理とメッセージパースのロジックを中心にテストします
        client = WebSocketClient(
            baseUrl = "ws://localhost:8000/",
            scope = testScope
        )
    }

    @After
    fun teardown() {
        client.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `初期状態はDisconnected`() = runTest {
        assertEquals(ConnectionState.Disconnected, client.connectionState.value)
    }

    @Test
    fun `AnalysisRequestのJSON変換が正しい`() {
        val request = AnalysisRequest(
            sessionId = "test-session",
            timestamp = "2024-01-01T00:00:00Z",
            emotionScores = mapOf(
                "happy" to 0.8f,
                "sad" to 0.1f
            ),
            audioData = "base64encodedaudio",
            audioFormat = "wav"
        )

        val adapter = moshi.adapter(AnalysisRequest::class.java)
        val json = adapter.toJson(request)

        assertTrue(json.contains("\"type\":\"ANALYSIS_REQUEST\""))
        assertTrue(json.contains("\"session_id\":\"test-session\""))
        assertTrue(json.contains("\"emotion_scores\":{"))
        assertTrue(json.contains("\"audio_data\":\"base64encodedaudio\""))
        assertTrue(json.contains("\"audio_format\":\"wav\""))
    }

    @Test
    fun `AnalysisResponseのJSON変換が正しい`() {
        val json = """
        {
            "type": "ANALYSIS_RESPONSE",
            "timestamp": "2024-01-01T00:00:00Z",
            "emotion": {
                "primary_emotion": "happy",
                "intensity": "high",
                "description": "相手は喜んでいます",
                "suggestion": "この話題を続けましょう"
            },
            "transcription": {
                "text": "こんにちは",
                "confidence": 0.95,
                "language": "ja",
                "duration_ms": 1000
            },
            "suggestions": [
                {
                    "text": "それは良かったですね",
                    "intent": "empathy"
                }
            ],
            "situation_analysis": "良好な会話",
            "processing_time_ms": 500
        }
        """.trimIndent()

        val adapter = moshi.adapter(AnalysisResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("ANALYSIS_RESPONSE", response?.type)
        assertEquals("happy", response?.emotion?.primaryEmotion)
        assertEquals("high", response?.emotion?.intensity)
        assertEquals("こんにちは", response?.transcription?.text)
        assertEquals(1, response?.suggestions?.size)
        assertEquals("それは良かったですね", response?.suggestions?.get(0)?.text)
    }

    @Test
    fun `PongMessageのJSON変換が正しい`() {
        val json = """
        {
            "type": "PONG",
            "timestamp": "2024-01-01T00:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(PongMessage::class.java)
        val pong = adapter.fromJson(json)

        assertNotNull(pong)
        assertEquals("PONG", pong?.type)
        assertEquals("2024-01-01T00:00:00Z", pong?.timestamp)
    }

    @Test
    fun `ResetAckMessageのJSON変換が正しい`() {
        val json = """
        {
            "type": "RESET_ACK",
            "timestamp": "2024-01-01T00:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ResetAckMessage::class.java)
        val ack = adapter.fromJson(json)

        assertNotNull(ack)
        assertEquals("RESET_ACK", ack?.type)
    }

    @Test
    fun `ServerErrorMessageのJSON変換が正しい`() {
        val json = """
        {
            "type": "ERROR",
            "message": "Internal server error",
            "detail": "Database connection failed",
            "timestamp": "2024-01-01T00:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ServerErrorMessage::class.java)
        val error = adapter.fromJson(json)

        assertNotNull(error)
        assertEquals("ERROR", error?.type)
        assertEquals("Internal server error", error?.message)
        assertEquals("Database connection failed", error?.detail)
    }

    @Test
    fun `PingMessageのJSON変換が正しい`() {
        val ping = PingMessage()

        val adapter = moshi.adapter(PingMessage::class.java)
        val json = adapter.toJson(ping)

        assertTrue(json.contains("\"type\":\"PING\""))
    }

    @Test
    fun `ResetMessageのJSON変換が正しい`() {
        val reset = ResetMessage()

        val adapter = moshi.adapter(ResetMessage::class.java)
        val json = adapter.toJson(reset)

        assertTrue(json.contains("\"type\":\"RESET\""))
    }

    @Test
    fun `ErrorReportMessageのJSON変換が正しい`() {
        val errorReport = ErrorReportMessage(message = "Client error occurred")

        val adapter = moshi.adapter(ErrorReportMessage::class.java)
        val json = adapter.toJson(errorReport)

        assertTrue(json.contains("\"type\":\"ERROR_REPORT\""))
        assertTrue(json.contains("\"message\":\"Client error occurred\""))
    }

    @Test
    fun `ConnectionState sealed classの各状態が正しく生成される`() {
        val disconnected = ConnectionState.Disconnected
        val connecting = ConnectionState.Connecting
        val connected = ConnectionState.Connected
        val reconnecting = ConnectionState.Reconnecting(attempt = 3, nextRetryMs = 4000L)
        val error = ConnectionState.Error(message = "Connection failed", code = 1001)

        assertTrue(disconnected is ConnectionState.Disconnected)
        assertTrue(connecting is ConnectionState.Connecting)
        assertTrue(connected is ConnectionState.Connected)
        assertTrue(reconnecting is ConnectionState.Reconnecting)
        assertEquals(3, (reconnecting as ConnectionState.Reconnecting).attempt)
        assertEquals(4000L, reconnecting.nextRetryMs)
        assertTrue(error is ConnectionState.Error)
        assertEquals("Connection failed", (error as ConnectionState.Error).message)
        assertEquals(1001, error.code)
    }

    @Test
    fun `EmotionInterpretationのJSON変換が正しい`() {
        val json = """
        {
            "primary_emotion": "happy",
            "intensity": "high",
            "description": "相手は喜んでいます",
            "suggestion": "この調子で続けましょう"
        }
        """.trimIndent()

        val adapter = moshi.adapter(EmotionInterpretation::class.java)
        val emotion = adapter.fromJson(json)

        assertNotNull(emotion)
        assertEquals("happy", emotion?.primaryEmotion)
        assertEquals("high", emotion?.intensity)
        assertEquals("相手は喜んでいます", emotion?.description)
        assertEquals("この調子で続けましょう", emotion?.suggestion)
    }

    @Test
    fun `TranscriptionResultのJSON変換が正しい`() {
        val json = """
        {
            "text": "こんにちは、元気ですか？",
            "confidence": 0.95,
            "language": "ja",
            "duration_ms": 2000
        }
        """.trimIndent()

        val adapter = moshi.adapter(TranscriptionResult::class.java)
        val transcription = adapter.fromJson(json)

        assertNotNull(transcription)
        assertEquals("こんにちは、元気ですか？", transcription?.text)
        assertEquals(0.95f, transcription?.confidence)
        assertEquals("ja", transcription?.language)
        assertEquals(2000, transcription?.durationMs)
    }

    @Test
    fun `ResponseSuggestionのJSON変換が正しい`() {
        val json = """
        {
            "text": "それは素晴らしいですね",
            "intent": "empathy"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ResponseSuggestion::class.java)
        val suggestion = adapter.fromJson(json)

        assertNotNull(suggestion)
        assertEquals("それは素晴らしいですね", suggestion?.text)
        assertEquals("empathy", suggestion?.intent)
    }

    @Test
    fun `AnalysisRequest with null audio fields`() {
        val request = AnalysisRequest(
            sessionId = "test-session",
            timestamp = "2024-01-01T00:00:00Z",
            emotionScores = mapOf("happy" to 0.5f),
            audioData = null,
            audioFormat = null
        )

        val adapter = moshi.adapter(AnalysisRequest::class.java)
        val json = adapter.toJson(request)

        assertTrue(json.contains("\"audio_data\":null"))
        assertTrue(json.contains("\"audio_format\":null"))

        // パースし直して確認
        val parsed = adapter.fromJson(json)
        assertNull(parsed?.audioData)
        assertNull(parsed?.audioFormat)
    }

    @Test
    fun `AnalysisResponse with null transcription`() {
        val json = """
        {
            "type": "ANALYSIS_RESPONSE",
            "timestamp": "2024-01-01T00:00:00Z",
            "emotion": {
                "primary_emotion": "neutral",
                "intensity": "low",
                "description": "感情が読み取れません",
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
        assertEquals(0, response?.suggestions?.size)
    }

    @Test
    fun `ServerErrorMessage with null detail`() {
        val json = """
        {
            "type": "ERROR",
            "message": "Unknown error",
            "detail": null,
            "timestamp": "2024-01-01T00:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ServerErrorMessage::class.java)
        val error = adapter.fromJson(json)

        assertNotNull(error)
        assertEquals("Unknown error", error?.message)
        assertNull(error?.detail)
    }

    // ========== セキュリティテスト ==========

    @Test
    fun `MessageTypeDto - 機密情報を含まずタイプのみ抽出`() {
        val json = """
        {
            "type": "PONG",
            "timestamp": "2024-01-01T00:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(MessageTypeDto::class.java)
        val messageType = adapter.fromJson(json)

        assertNotNull(messageType)
        assertEquals("PONG", messageType?.type)
    }

    @Test
    fun `AnalysisRequest - 大量の感情スコアをサポート`() {
        val emotionScores = (0..100).associate { i ->
            "emotion_$i" to (Math.random().toFloat())
        }

        val request = AnalysisRequest(
            sessionId = "test",
            timestamp = "2024-01-01T00:00:00Z",
            emotionScores = emotionScores,
            audioData = null,
            audioFormat = null
        )

        val adapter = moshi.adapter(AnalysisRequest::class.java)
        val json = adapter.toJson(request)
        val parsed = adapter.fromJson(json)

        assertNotNull(parsed)
        assertEquals(101, parsed?.emotionScores?.size)
    }

    @Test
    fun `AnalysisRequest - 大きなBase64オーディオデータをサポート`() {
        val largeAudioData = "SGVsbG8gV29ybGQ=".repeat(1000)  // ~15KB

        val request = AnalysisRequest(
            sessionId = "test",
            timestamp = "2024-01-01T00:00:00Z",
            emotionScores = mapOf("happy" to 0.5f),
            audioData = largeAudioData,
            audioFormat = "wav"
        )

        val adapter = moshi.adapter(AnalysisRequest::class.java)
        val json = adapter.toJson(request)

        assertTrue(json.length > 10000)  // 大きなデータ

        val parsed = adapter.fromJson(json)
        assertEquals(largeAudioData, parsed?.audioData)
    }

    @Test
    fun `AnalysisResponse - 複数の提案をサポート`() {
        val json = """
        {
            "type": "ANALYSIS_RESPONSE",
            "timestamp": "2024-01-01T00:00:00Z",
            "emotion": {
                "primary_emotion": "confused",
                "intensity": "medium",
                "description": "相手は混乱しているようです",
                "suggestion": "より詳しく説明してください"
            },
            "transcription": {
                "text": "え？何ですか？",
                "confidence": 0.87,
                "language": "ja",
                "duration_ms": 800
            },
            "suggestions": [
                {"text": "具体例を挙げて説明する", "intent": "clarify"},
                {"text": "別のアプローチで説明する", "intent": "rephrase"},
                {"text": "一度確認してから進める", "intent": "confirm"}
            ],
            "situation_analysis": "相手が話の内容を理解できていない",
            "processing_time_ms": 700
        }
        """.trimIndent()

        val adapter = moshi.adapter(AnalysisResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals(3, response?.suggestions?.size)
        assertEquals("clarify", response?.suggestions?.get(0)?.intent)
        assertEquals("confirm", response?.suggestions?.get(2)?.intent)
    }

    // ========== メモリリーク・リソース解放テスト ==========

    @Test
    fun `WebSocketClient - close()が安全に呼び出される`() {
        // close()が例外を発生させないことを確認
        assertDoesNotThrow {
            client.close()
        }
    }

    @Test
    fun `ConnectionState - マッピング処理が高速`() {
        // 大量の状態遷移が効率的に処理されることを確認
        val startTime = System.currentTimeMillis()
        repeat(10000) { i ->
            when (i % 5) {
                0 -> ConnectionState.Disconnected
                1 -> ConnectionState.Connecting
                2 -> ConnectionState.Connected
                3 -> ConnectionState.Reconnecting(i, 1000L)
                else -> ConnectionState.Error("Test error")
            }
        }
        val duration = System.currentTimeMillis() - startTime

        assertTrue("Mapping too slow: ${duration}ms", duration < 100)  // 100ms以内
    }

    // ========== 入力検証テスト ==========

    @Test
    fun `AnalysisRequest - 空の感情スコアマップは許可される`() {
        val request = AnalysisRequest(
            sessionId = "test",
            timestamp = "2024-01-01T00:00:00Z",
            emotionScores = emptyMap(),
            audioData = null,
            audioFormat = null
        )

        val adapter = moshi.adapter(AnalysisRequest::class.java)
        val json = adapter.toJson(request)
        val parsed = adapter.fromJson(json)

        assertNotNull(parsed)
        assertEquals(0, parsed?.emotionScores?.size)
    }

    @Test
    fun `AnalysisRequest - タイムスタンプのISO8601形式`() {
        val timestamps = listOf(
            "2024-01-01T00:00:00Z",
            "2024-12-31T23:59:59Z",
            "2024-06-15T12:30:45Z"
        )

        timestamps.forEach { timestamp ->
            val request = AnalysisRequest(
                sessionId = "test",
                timestamp = timestamp,
                emotionScores = mapOf("happy" to 0.5f),
                audioData = null,
                audioFormat = null
            )

            val adapter = moshi.adapter(AnalysisRequest::class.java)
            val json = adapter.toJson(request)
            val parsed = adapter.fromJson(json)

            assertEquals(timestamp, parsed?.timestamp)
        }
    }
}
