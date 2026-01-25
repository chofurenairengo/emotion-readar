package com.commuxr.android.data.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * WebSocketメッセージDTOのユニットテスト
 */
class WebSocketMessageDtoTest {

    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    // ========== PingMessage Tests ==========

    @Test
    fun `PingMessage - デフォルト値でJSON変換できる`() {
        val ping = PingMessage()

        val adapter = moshi.adapter(PingMessage::class.java)
        val json = adapter.toJson(ping)

        assertTrue(json.contains("\"type\":\"PING\""))

        val parsed = adapter.fromJson(json)
        assertEquals("PING", parsed?.type)
    }

    // ========== PongMessage Tests ==========

    @Test
    fun `PongMessage - サーバーレスポンスをパースできる`() {
        val json = """
        {
            "type": "PONG",
            "timestamp": "2024-01-01T12:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(PongMessage::class.java)
        val pong = adapter.fromJson(json)

        assertNotNull(pong)
        assertEquals("PONG", pong?.type)
        assertEquals("2024-01-01T12:00:00Z", pong?.timestamp)
    }

    @Test
    fun `PongMessage - 異なるタイムスタンプ形式`() {
        val timestamps = listOf(
            "2024-01-01T12:00:00Z",
            "2024-01-01T12:00:00.123Z",
            "2024-01-01T12:00:00.123456Z"
        )

        timestamps.forEach { timestamp ->
            val json = """{"type": "PONG", "timestamp": "$timestamp"}"""

            val adapter = moshi.adapter(PongMessage::class.java)
            val pong = adapter.fromJson(json)

            assertEquals(timestamp, pong?.timestamp)
        }
    }

    // ========== ResetMessage Tests ==========

    @Test
    fun `ResetMessage - デフォルト値でJSON変換できる`() {
        val reset = ResetMessage()

        val adapter = moshi.adapter(ResetMessage::class.java)
        val json = adapter.toJson(reset)

        assertTrue(json.contains("\"type\":\"RESET\""))

        val parsed = adapter.fromJson(json)
        assertEquals("RESET", parsed?.type)
    }

    // ========== ResetAckMessage Tests ==========

    @Test
    fun `ResetAckMessage - サーバーレスポンスをパースできる`() {
        val json = """
        {
            "type": "RESET_ACK",
            "timestamp": "2024-01-01T12:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ResetAckMessage::class.java)
        val ack = adapter.fromJson(json)

        assertNotNull(ack)
        assertEquals("RESET_ACK", ack?.type)
        assertEquals("2024-01-01T12:00:00Z", ack?.timestamp)
    }

    // ========== ErrorReportMessage Tests ==========

    @Test
    fun `ErrorReportMessage - エラーメッセージでJSON変換できる`() {
        val errorReport = ErrorReportMessage(message = "Camera permission denied")

        val adapter = moshi.adapter(ErrorReportMessage::class.java)
        val json = adapter.toJson(errorReport)

        assertTrue(json.contains("\"type\":\"ERROR_REPORT\""))
        assertTrue(json.contains("\"message\":\"Camera permission denied\""))

        val parsed = adapter.fromJson(json)
        assertEquals("ERROR_REPORT", parsed?.type)
        assertEquals("Camera permission denied", parsed?.message)
    }

    @Test
    fun `ErrorReportMessage - 各種エラーメッセージ`() {
        val messages = listOf(
            "Network connection failed",
            "MediaPipe initialization error",
            "WebSocket disconnected unexpectedly",
            "Audio recording permission denied"
        )

        messages.forEach { message ->
            val errorReport = ErrorReportMessage(message = message)

            val adapter = moshi.adapter(ErrorReportMessage::class.java)
            val json = adapter.toJson(errorReport)
            val parsed = adapter.fromJson(json)

            assertEquals(message, parsed?.message)
        }
    }

    @Test
    fun `ErrorReportMessage - 空のメッセージ`() {
        val errorReport = ErrorReportMessage(message = "")

        val adapter = moshi.adapter(ErrorReportMessage::class.java)
        val json = adapter.toJson(errorReport)
        val parsed = adapter.fromJson(json)

        assertEquals("", parsed?.message)
    }

    @Test
    fun `ErrorReportMessage - 長いメッセージ`() {
        val longMessage = "Error: " + "details ".repeat(100)
        val errorReport = ErrorReportMessage(message = longMessage)

        val adapter = moshi.adapter(ErrorReportMessage::class.java)
        val json = adapter.toJson(errorReport)
        val parsed = adapter.fromJson(json)

        assertEquals(longMessage, parsed?.message)
    }

    // ========== ErrorAckMessage Tests ==========

    @Test
    fun `ErrorAckMessage - サーバーレスポンスをパースできる`() {
        val json = """
        {
            "type": "ERROR_ACK",
            "timestamp": "2024-01-01T12:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ErrorAckMessage::class.java)
        val ack = adapter.fromJson(json)

        assertNotNull(ack)
        assertEquals("ERROR_ACK", ack?.type)
        assertEquals("2024-01-01T12:00:00Z", ack?.timestamp)
    }

    // ========== ServerErrorMessage Tests ==========

    @Test
    fun `ServerErrorMessage - 詳細ありエラーをパースできる`() {
        val json = """
        {
            "type": "ERROR",
            "message": "Internal server error",
            "detail": "Database connection timeout after 30 seconds",
            "timestamp": "2024-01-01T12:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ServerErrorMessage::class.java)
        val error = adapter.fromJson(json)

        assertNotNull(error)
        assertEquals("ERROR", error?.type)
        assertEquals("Internal server error", error?.message)
        assertEquals("Database connection timeout after 30 seconds", error?.detail)
        assertEquals("2024-01-01T12:00:00Z", error?.timestamp)
    }

    @Test
    fun `ServerErrorMessage - 詳細なしエラーをパースできる`() {
        val json = """
        {
            "type": "ERROR",
            "message": "Unknown error",
            "detail": null,
            "timestamp": "2024-01-01T12:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ServerErrorMessage::class.java)
        val error = adapter.fromJson(json)

        assertNotNull(error)
        assertEquals("Unknown error", error?.message)
        assertNull(error?.detail)
    }

    @Test
    fun `ServerErrorMessage - 各種サーバーエラー`() {
        val errors = listOf(
            "Internal server error" to "Database connection failed",
            "Service unavailable" to "LLM API rate limit exceeded",
            "Bad request" to "Invalid session_id format",
            "Unauthorized" to "Session expired",
            "Forbidden" to "Permission denied"
        )

        errors.forEach { (message, detail) ->
            val json = """
            {
                "type": "ERROR",
                "message": "$message",
                "detail": "$detail",
                "timestamp": "2024-01-01T12:00:00Z"
            }
            """.trimIndent()

            val adapter = moshi.adapter(ServerErrorMessage::class.java)
            val error = adapter.fromJson(json)

            assertEquals(message, error?.message)
            assertEquals(detail, error?.detail)
        }
    }

    // ========== Round-trip Tests ==========

    @Test
    fun `PingMessage - ラウンドトリップ変換`() {
        val original = PingMessage()

        val adapter = moshi.adapter(PingMessage::class.java)
        val json = adapter.toJson(original)
        val parsed = adapter.fromJson(json)

        assertEquals(original.type, parsed?.type)
    }

    @Test
    fun `ResetMessage - ラウンドトリップ変換`() {
        val original = ResetMessage()

        val adapter = moshi.adapter(ResetMessage::class.java)
        val json = adapter.toJson(original)
        val parsed = adapter.fromJson(json)

        assertEquals(original.type, parsed?.type)
    }

    @Test
    fun `ErrorReportMessage - ラウンドトリップ変換`() {
        val original = ErrorReportMessage(message = "Test error")

        val adapter = moshi.adapter(ErrorReportMessage::class.java)
        val json = adapter.toJson(original)
        val parsed = adapter.fromJson(json)

        assertEquals(original.type, parsed?.type)
        assertEquals(original.message, parsed?.message)
    }

    // ========== Type Field Validation ==========

    @Test
    fun `全メッセージタイプが正しいtype値を持つ`() {
        assertEquals("PING", PingMessage().type)
        assertEquals("RESET", ResetMessage().type)
        assertEquals("ERROR_REPORT", ErrorReportMessage("test").type)
    }

    @Test
    fun `サーバーレスポンスメッセージが正しくパースされる`() {
        val pongJson = """{"type": "PONG", "timestamp": "2024-01-01T12:00:00Z"}"""
        val resetAckJson = """{"type": "RESET_ACK", "timestamp": "2024-01-01T12:00:00Z"}"""
        val errorAckJson = """{"type": "ERROR_ACK", "timestamp": "2024-01-01T12:00:00Z"}"""
        val errorJson = """{"type": "ERROR", "message": "error", "detail": null, "timestamp": "2024-01-01T12:00:00Z"}"""

        val pong = moshi.adapter(PongMessage::class.java).fromJson(pongJson)
        val resetAck = moshi.adapter(ResetAckMessage::class.java).fromJson(resetAckJson)
        val errorAck = moshi.adapter(ErrorAckMessage::class.java).fromJson(errorAckJson)
        val error = moshi.adapter(ServerErrorMessage::class.java).fromJson(errorJson)

        assertEquals("PONG", pong?.type)
        assertEquals("RESET_ACK", resetAck?.type)
        assertEquals("ERROR_ACK", errorAck?.type)
        assertEquals("ERROR", error?.type)
    }

    // ========== Edge Cases ==========

    @Test
    fun `ErrorReportMessage - 特殊文字を含むメッセージ`() {
        val specialChars = "Error: \"quoted\" & <tags> \\ / \n \t \r"
        val errorReport = ErrorReportMessage(message = specialChars)

        val adapter = moshi.adapter(ErrorReportMessage::class.java)
        val json = adapter.toJson(errorReport)
        val parsed = adapter.fromJson(json)

        // Moshiがエスケープ処理を行うため、パース後も元の文字列が復元される
        assertEquals(specialChars, parsed?.message)
    }

    @Test
    fun `ServerErrorMessage - Unicode文字を含むエラー`() {
        val json = """
        {
            "type": "ERROR",
            "message": "日本語エラーメッセージ",
            "detail": "詳細: データベース接続エラー 🔥",
            "timestamp": "2024-01-01T12:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ServerErrorMessage::class.java)
        val error = adapter.fromJson(json)

        assertEquals("日本語エラーメッセージ", error?.message)
        assertEquals("詳細: データベース接続エラー 🔥", error?.detail)
    }

    @Test
    fun `タイムスタンプフィールドの一貫性`() {
        val timestamp = "2024-01-01T12:00:00Z"

        val pongJson = """{"type": "PONG", "timestamp": "$timestamp"}"""
        val resetAckJson = """{"type": "RESET_ACK", "timestamp": "$timestamp"}"""
        val errorAckJson = """{"type": "ERROR_ACK", "timestamp": "$timestamp"}"""
        val errorJson = """{"type": "ERROR", "message": "test", "detail": null, "timestamp": "$timestamp"}"""

        val pong = moshi.adapter(PongMessage::class.java).fromJson(pongJson)
        val resetAck = moshi.adapter(ResetAckMessage::class.java).fromJson(resetAckJson)
        val errorAck = moshi.adapter(ErrorAckMessage::class.java).fromJson(errorAckJson)
        val error = moshi.adapter(ServerErrorMessage::class.java).fromJson(errorJson)

        // すべてのレスポンスメッセージが同じタイムスタンプ形式をサポート
        assertEquals(timestamp, pong?.timestamp)
        assertEquals(timestamp, resetAck?.timestamp)
        assertEquals(timestamp, errorAck?.timestamp)
        assertEquals(timestamp, error?.timestamp)
    }

    // ========== MessageTypeDto Tests ==========

    @Test
    fun `MessageTypeDto - 各メッセージタイプを正しく識別`() {
        val messageTypes = listOf(
            "PING",
            "PONG",
            "RESET",
            "RESET_ACK",
            "ERROR_REPORT",
            "ERROR_ACK",
            "ERROR",
            "ANALYSIS_REQUEST",
            "ANALYSIS_RESPONSE"
        )

        val adapter = moshi.adapter(MessageTypeDto::class.java)

        messageTypes.forEach { type ->
            val json = """{"type": "$type"}"""
            val dto = adapter.fromJson(json)
            assertEquals(type, dto?.type)
        }
    }

    @Test
    fun `MessageTypeDto - 追加フィールドがあっても タイプのみ抽出可能`() {
        val json = """
        {
            "type": "ANALYSIS_RESPONSE",
            "timestamp": "2024-01-01T00:00:00Z",
            "emotion": {
                "primary_emotion": "happy",
                "intensity": "high"
            },
            "suggestions": [
                {"text": "素晴らしい", "intent": "empathy"}
            ]
        }
        """.trimIndent()

        val adapter = moshi.adapter(MessageTypeDto::class.java)
        val dto = adapter.fromJson(json)

        assertNotNull(dto)
        assertEquals("ANALYSIS_RESPONSE", dto?.type)
    }

    @Test
    fun `MessageTypeDto - null 値は許可されない`() {
        val json = """{"type": null}"""

        val adapter = moshi.adapter(MessageTypeDto::class.java)
        val dto = adapter.fromJson(json)

        assertNull(dto)  // 必須フィールドが null なので null が返される
    }

    @Test
    fun `MessageTypeDto - 未知のタイプもサポート`() {
        val unknownType = "UNKNOWN_MESSAGE_TYPE_12345"
        val json = """{"type": "$unknownType"}"""

        val adapter = moshi.adapter(MessageTypeDto::class.java)
        val dto = adapter.fromJson(json)

        assertEquals(unknownType, dto?.type)
    }
}
