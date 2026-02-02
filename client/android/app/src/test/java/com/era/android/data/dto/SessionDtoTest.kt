package com.era.android.data.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionDtoTest {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `SessionResponse parses correctly from JSON`() {
        val json = """
            {
                "id": "abc123",
                "status": "active",
                "started_at": "2024-01-01T12:00:00Z",
                "ended_at": null
            }
        """.trimIndent()

        val adapter = moshi.adapter(SessionResponse::class.java)
        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertEquals("abc123", result?.id)
        assertEquals("active", result?.status)
        assertEquals("2024-01-01T12:00:00Z", result?.startedAt)
        assertNull(result?.endedAt)
    }

    @Test
    fun `SessionResponse parses ended session correctly`() {
        val json = """
            {
                "id": "abc123",
                "status": "ended",
                "started_at": "2024-01-01T12:00:00Z",
                "ended_at": "2024-01-01T13:00:00Z"
            }
        """.trimIndent()

        val adapter = moshi.adapter(SessionResponse::class.java)
        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertEquals("abc123", result?.id)
        assertEquals("ended", result?.status)
        assertEquals("2024-01-01T12:00:00Z", result?.startedAt)
        assertEquals("2024-01-01T13:00:00Z", result?.endedAt)
    }

    @Test
    fun `SessionResponse serializes to JSON correctly`() {
        val session = SessionResponse(
            id = "test456",
            status = "active",
            startedAt = "2024-02-15T10:30:00Z",
            endedAt = null
        )

        val adapter = moshi.adapter(SessionResponse::class.java)
        val json = adapter.toJson(session)

        assertNotNull(json)
        assert(json.contains("\"id\":\"test456\""))
        assert(json.contains("\"status\":\"active\""))
        assert(json.contains("\"started_at\":\"2024-02-15T10:30:00Z\""))
    }

    @Test
    fun `HealthResponse parses correctly from JSON`() {
        val json = """
            {
                "status": "ok"
            }
        """.trimIndent()

        val adapter = moshi.adapter(HealthResponse::class.java)
        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertEquals("ok", result?.status)
    }

    @Test
    fun `ErrorResponse parses correctly from JSON`() {
        val json = """
            {
                "detail": "Session not found"
            }
        """.trimIndent()

        val adapter = moshi.adapter(ErrorResponse::class.java)
        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertEquals("Session not found", result?.detail)
    }
}
