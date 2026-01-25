package com.commuxr.android.data.repository

import com.commuxr.android.data.api.ApiResult
import com.commuxr.android.data.api.CommXRApi
import com.commuxr.android.data.dto.HealthResponse
import com.commuxr.android.data.dto.SessionResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class SessionRepositoryTest {
    private lateinit var api: CommXRApi
    private lateinit var repository: SessionRepository

    @Before
    fun setup() {
        api = mockk()
        repository = SessionRepository(api)
    }

    @Test
    fun `createSession returns Success when API succeeds`() = runTest {
        val session = SessionResponse(
            id = "abc123",
            status = "active",
            startedAt = "2024-01-01T12:00:00Z",
            endedAt = null
        )
        coEvery { api.createSession() } returns Response.success(201, session)

        val result = repository.createSession()

        assertTrue(result is ApiResult.Success)
        assertEquals("abc123", (result as ApiResult.Success).data.id)
        assertEquals("abc123", repository.currentSessionId)
        assertTrue(repository.isSessionActive)
    }

    @Test
    fun `createSession returns Error when API fails`() = runTest {
        coEvery { api.createSession() } returns Response.error(
            500,
            "Internal Server Error".toResponseBody()
        )

        val result = repository.createSession()

        assertTrue(result is ApiResult.Error)
        assertEquals(500, (result as ApiResult.Error).code)
    }

    @Test
    fun `createSession returns NetworkError on IOException`() = runTest {
        coEvery { api.createSession() } throws IOException("Network error")

        val result = repository.createSession()

        assertTrue(result is ApiResult.NetworkError)
        assertNotNull((result as ApiResult.NetworkError).throwable)
    }

    @Test
    fun `endSession returns Success when API succeeds`() = runTest {
        val activeSession = SessionResponse(
            id = "abc123",
            status = "active",
            startedAt = "2024-01-01T12:00:00Z",
            endedAt = null
        )
        val endedSession = SessionResponse(
            id = "abc123",
            status = "ended",
            startedAt = "2024-01-01T12:00:00Z",
            endedAt = "2024-01-01T13:00:00Z"
        )
        coEvery { api.createSession() } returns Response.success(201, activeSession)
        coEvery { api.endSession("abc123") } returns Response.success(200, endedSession)

        repository.createSession()
        val result = repository.endSession()

        assertTrue(result is ApiResult.Success)
        assertEquals("ended", (result as ApiResult.Success).data.status)
        assertFalse(repository.isSessionActive)
    }

    @Test
    fun `endSession returns Error when no active session`() = runTest {
        val result = repository.endSession()

        assertTrue(result is ApiResult.Error)
        assertNull((result as ApiResult.Error).code)
        assertEquals("No active session", result.message)
    }

    @Test
    fun `endSession returns Error when API fails`() = runTest {
        coEvery { api.endSession("abc123") } returns Response.error(
            404,
            "Not found".toResponseBody()
        )

        val result = repository.endSession("abc123")

        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).code)
    }

    @Test
    fun `getSession returns Success when API succeeds`() = runTest {
        val session = SessionResponse(
            id = "test456",
            status = "active",
            startedAt = "2024-02-01T10:00:00Z",
            endedAt = null
        )
        coEvery { api.getSession("test456") } returns Response.success(200, session)

        val result = repository.getSession("test456")

        assertTrue(result is ApiResult.Success)
        assertEquals("test456", (result as ApiResult.Success).data.id)
    }

    @Test
    fun `getSession returns Error when session not found`() = runTest {
        coEvery { api.getSession("invalid") } returns Response.error(
            404,
            "Not found".toResponseBody()
        )

        val result = repository.getSession("invalid")

        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).code)
    }

    @Test
    fun `healthCheck returns Success when server is healthy`() = runTest {
        coEvery { api.healthCheck() } returns Response.success(
            200,
            HealthResponse(status = "ok")
        )

        val result = repository.healthCheck()

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)
    }

    @Test
    fun `healthCheck returns Error when server is unhealthy`() = runTest {
        coEvery { api.healthCheck() } returns Response.success(
            200,
            HealthResponse(status = "error")
        )

        val result = repository.healthCheck()

        assertTrue(result is ApiResult.Error)
        assertEquals("Server unhealthy", (result as ApiResult.Error).message)
    }

    @Test
    fun `healthCheck returns NetworkError on IOException`() = runTest {
        coEvery { api.healthCheck() } throws IOException("Connection failed")

        val result = repository.healthCheck()

        assertTrue(result is ApiResult.NetworkError)
    }

    @Test
    fun `clearSession clears current session`() = runTest {
        val session = SessionResponse(
            id = "abc123",
            status = "active",
            startedAt = "2024-01-01T12:00:00Z",
            endedAt = null
        )
        coEvery { api.createSession() } returns Response.success(201, session)

        repository.createSession()
        assertNotNull(repository.currentSessionId)

        repository.clearSession()
        assertNull(repository.currentSessionId)
        assertFalse(repository.isSessionActive)
    }
}
