package com.commuxr.unityplugin.data.websocket

import android.util.Log
import com.commuxr.unityplugin.data.api.ApiClient
import com.commuxr.unityplugin.data.dto.AnalysisRequest
import com.commuxr.unityplugin.data.dto.AnalysisResponse
import com.commuxr.unityplugin.data.dto.ErrorAckMessage
import com.commuxr.unityplugin.data.dto.ErrorReportMessage
import com.commuxr.unityplugin.data.dto.MessageTypeDto
import com.commuxr.unityplugin.data.dto.PingMessage
import com.commuxr.unityplugin.data.dto.ResetAckMessage
import com.commuxr.unityplugin.data.dto.ResetMessage
import com.commuxr.unityplugin.data.dto.ServerErrorMessage
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * WebSocket通信クライアント。
 */
class WebSocketClient(
    private val baseUrl: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : Closeable {

    private var webSocket: WebSocket? = null
    private var currentSessionId: String? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastPongReceivedAt: Long = 0
    private var reconnectAttempt = 0

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _analysisResponses = MutableSharedFlow<AnalysisResponse>()
    val analysisResponses: SharedFlow<AnalysisResponse> = _analysisResponses.asSharedFlow()

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        ApiClient.okHttpClient.newBuilder()
            .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    fun connect(sessionId: String) {
        if (_connectionState.value is ConnectionState.Connected ||
            _connectionState.value is ConnectionState.Connecting) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        currentSessionId = sessionId
        reconnectAttempt = 0
        attemptConnect()
    }

    fun disconnect() {
        stopHeartbeat()
        stopReconnect()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun sendAnalysisRequest(
        emotionScores: Map<String, Float>,
        audioData: String? = null,
        audioFormat: String? = null,
    ) {
        val sessionId = currentSessionId ?: run {
            Log.e(TAG, "Cannot send ANALYSIS_REQUEST: no session ID")
            return
        }

        val message = AnalysisRequest(
            sessionId = sessionId,
            timestamp = java.time.Instant.now().toString(),
            emotionScores = emotionScores,
            audioData = audioData,
            audioFormat = audioFormat,
        )
        sendMessage(message)
    }

    fun sendReset() {
        sendMessage(ResetMessage())
    }

    fun sendErrorReport(errorMessage: String) {
        sendMessage(ErrorReportMessage(message = errorMessage))
    }

    override fun close() {
        try {
            disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
        try {
            scope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling scope", e)
        }
    }

    private fun attemptConnect() {
        val sessionId = currentSessionId ?: return
        _connectionState.value = ConnectionState.Connecting

        val normalized = normalizeBaseUrl(baseUrl)
        val url = "${normalized}api/realtime?session_id=$sessionId"
        Log.d(TAG, "Attempting WebSocket connection: $url")

        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, WebSocketListenerImpl())
    }

    private fun <T> sendMessage(message: T) {
        val ws = webSocket
        if (ws == null || _connectionState.value !is ConnectionState.Connected) {
            Log.e(TAG, "Cannot send message: WebSocket not connected")
            return
        }

        try {
            @Suppress("UNCHECKED_CAST")
            val adapter = moshi.adapter(message!!::class.java) as JsonAdapter<T>
            val json = adapter.toJson(message)
            ws.send(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        lastPongReceivedAt = System.currentTimeMillis()

        heartbeatJob = scope.launch {
            try {
                while (true) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    val now = System.currentTimeMillis()
                    if (now - lastPongReceivedAt > PONG_TIMEOUT_MS) {
                        triggerReconnect("Heartbeat timeout")
                        break
                    }
                    sendMessage(PingMessage())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Heartbeat error", e)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun triggerReconnect(reason: String) {
        Log.w(TAG, "Trigger reconnect: $reason")
        stopHeartbeat()
        webSocket?.close(1000, reason)
        webSocket = null

        if (_connectionState.value is ConnectionState.Disconnected) {
            return
        }

        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = ConnectionState.Error("Max reconnection attempts reached")
            return
        }

        stopReconnect()
        reconnectJob = scope.launch {
            while (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempt++
                val waitMs = calculateBackoffDelay(reconnectAttempt)
                _connectionState.value = ConnectionState.Reconnecting(reconnectAttempt, waitMs)
                delay(waitMs)
                attemptConnect()
                delay(4_000)
                if (_connectionState.value is ConnectionState.Connected) {
                    reconnectAttempt = 0
                    return@launch
                }
            }
            _connectionState.value = ConnectionState.Error("Max reconnection attempts reached")
        }
    }

    private fun stopReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponent = (attempt - 1).coerceAtMost(5)
        val delayMs = BASE_DELAY_MS * (1 shl exponent)
        return delayMs.coerceAtMost(MAX_DELAY_MS)
    }

    private fun handleMessage(text: String) {
        val type = try {
            moshi.adapter(MessageTypeDto::class.java).fromJson(text)?.type
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse message type", e)
            null
        }

        when (type) {
            "PONG" -> lastPongReceivedAt = System.currentTimeMillis()
            "RESET_ACK" -> moshi.adapter(ResetAckMessage::class.java).fromJson(text)
            "ERROR_ACK" -> moshi.adapter(ErrorAckMessage::class.java).fromJson(text)
            "ANALYSIS_RESPONSE" -> {
                val response = moshi.adapter(AnalysisResponse::class.java).fromJson(text)
                if (response != null) {
                    scope.launch { _analysisResponses.emit(response) }
                }
            }
            "ERROR" -> {
                val error = moshi.adapter(ServerErrorMessage::class.java).fromJson(text)
                _connectionState.value = ConnectionState.Error(error?.message ?: "Server error")
            }
            else -> Log.w(TAG, "Unknown message type: $type")
        }
    }

    private inner class WebSocketListenerImpl : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = ConnectionState.Connected
            reconnectAttempt = 0
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            stopHeartbeat()
            if (_connectionState.value !is ConnectionState.Disconnected) {
                triggerReconnect("Closed: $code $reason")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            stopHeartbeat()
            if (_connectionState.value !is ConnectionState.Disconnected) {
                triggerReconnect("Failure: ${t.message}")
            }
        }
    }

    companion object {
        private const val TAG = "WebSocketClient"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val PONG_TIMEOUT_MS = 60_000L
        private const val BASE_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 32_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val PING_INTERVAL_MS = 30_000L

        private fun normalizeBaseUrl(raw: String): String {
            val withScheme = if (raw.startsWith("ws://") || raw.startsWith("wss://")) {
                raw
            } else {
                "ws://$raw"
            }
            return if (withScheme.endsWith('/')) withScheme else "$withScheme/"
        }
    }
}
