package com.commuxr.android.data.websocket

import android.util.Log
import com.commuxr.android.data.api.ApiClient
import com.commuxr.android.data.dto.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * WebSocket通信クライアント
 *
 * サーバーとのリアルタイム双方向通信を管理します。
 * - 接続状態管理
 * - PING/PONGハートビート（30秒間隔）
 * - 指数バックオフ再接続（1s → 2s → 4s → 8s → 16s → 32s）
 * - 各種メッセージ送受信（ANALYSIS_REQUEST, RESET, ERROR_REPORTなど）
 *
 * @property baseUrl WebSocketサーバーのベースURL（ws://またはwss://）
 * @property scope Coroutineスコープ
 */
class WebSocketClient(
    private val baseUrl: String = getWsBaseUrl(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val httpClient: OkHttpClient? = null
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
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        (httpClient ?: ApiClient.okHttpClient).newBuilder()
            .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    companion object {
        private const val TAG = "WebSocketClient"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L  // 30秒
        private const val PONG_TIMEOUT_MS = 60_000L  // 60秒
        private const val BASE_DELAY_MS = 1_000L  // 1秒
        private const val MAX_DELAY_MS = 32_000L  // 32秒
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val PING_INTERVAL_MS = 30_000L

        private fun getWsBaseUrl(): String {
            return "ws://server/"
        }
    }

    /**
     * WebSocket接続を確立
     *
     * @param sessionId セッションID
     */
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

    /**
     * WebSocket接続を切断
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        stopHeartbeat()
        stopReconnect()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * PING送信
     */
    fun sendPing() {
        val message = PingMessage()
        sendMessage(message)
    }

    /**
     * ANALYSIS_REQUEST送信
     *
     * @param emotionScores 感情スコアマップ
     * @param audioData Base64エンコードされた音声データ（オプション）
     * @param audioFormat 音声フォーマット（オプション）
     */
    fun sendAnalysisRequest(
        emotionScores: Map<String, Float>,
        audioData: String? = null,
        audioFormat: String? = null
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
            audioFormat = audioFormat
        )
        sendMessage(message)
    }

    /**
     * RESET送信
     */
    fun sendReset() {
        val message = ResetMessage()
        sendMessage(message)
    }

    /**
     * ERROR_REPORT送信
     *
     * @param errorMessage エラーメッセージ
     */
    fun sendErrorReport(errorMessage: String) {
        val message = ErrorReportMessage(message = errorMessage)
        sendMessage(message)
    }

    /**
     * リソース解放
     *
     * 接続切断とスコープキャンセルを行う。例外があっても処理は続行する
     */
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

    // ========== Private Methods ==========

    private fun attemptConnect() {
        val sessionId = currentSessionId ?: return
        _connectionState.value = ConnectionState.Connecting

        val url = "${baseUrl}api/realtime?session_id=$sessionId"
        Log.d(TAG, "Attempting WebSocket connection")  // セッションIDを含めない

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, WebSocketListenerImpl())
    }

    private fun <T> sendMessage(message: T) {
        val ws = webSocket
        if (ws == null) {
            Log.e(TAG, "Cannot send message: WebSocket not connected")
            return
        }

        try {
            @Suppress("UNCHECKED_CAST")
            val adapter = moshi.adapter(message!!::class.java) as JsonAdapter<T>
            val json = adapter.toJson(message)
            Log.d(TAG, "Sending message type: ${message!!::class.simpleName}")  // 機密情報を含めない
            ws.send(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.javaClass.simpleName}", e)
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        lastPongReceivedAt = System.currentTimeMillis()

        heartbeatJob = scope.launch {
            try {
                while (isActive) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    val now = System.currentTimeMillis()

                    if (now - lastPongReceivedAt > PONG_TIMEOUT_MS) {
                        Log.w(TAG, "Heartbeat timeout - triggering reconnect")
                        triggerReconnect("Heartbeat timeout")
                        break
                    }

                    sendPing()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Heartbeat job cancelled")
                throw e  // CancellationExceptionは再スロー
            } catch (e: Exception) {
                Log.e(TAG, "Heartbeat error: ${e.javaClass.simpleName}", e)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun triggerReconnect(reason: String) {
        Log.d(TAG, "Triggering reconnect: $reason")
        stopHeartbeat()
        webSocket?.close(1000, reason)
        webSocket = null

        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnection attempts reached")
            _connectionState.value = ConnectionState.Error("Max reconnection attempts reached")
            return
        }

        stopReconnect()
        reconnectJob = scope.launch {
            try {
                while (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempt++
                    val delay = calculateBackoffDelay(reconnectAttempt)
                    _connectionState.value = ConnectionState.Reconnecting(reconnectAttempt, delay)
                    Log.d(TAG, "Reconnection attempt $reconnectAttempt, waiting ${delay}ms")

                    delay(delay)
                    attemptConnect()

                    // Wait for connection result
                    delay(5000)
                    if (_connectionState.value is ConnectionState.Connected) {
                        reconnectAttempt = 0
                        return@launch
                    }
                }

                _connectionState.value = ConnectionState.Error("Max reconnection attempts reached")
            } catch (e: CancellationException) {
                Log.d(TAG, "Reconnect job cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect error: ${e.javaClass.simpleName}", e)
                _connectionState.value = ConnectionState.Error("Reconnect error: ${e.message}")
            }
        }
    }

    private fun stopReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponent = (attempt - 1).coerceAtMost(5)  // max 2^5 = 32
        val delay = BASE_DELAY_MS * (1 shl exponent)
        return delay.coerceAtMost(MAX_DELAY_MS)
    }

    private fun handleMessage(text: String) {
        try {
            // メッセージタイプを判定
            val typeAdapter = moshi.adapter(MessageTypeDto::class.java)
            val messageType = try {
                typeAdapter.fromJson(text)?.type
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse message type", e)
                return
            }

            when (messageType) {
                "PONG" -> handlePong(text)
                "RESET_ACK" -> handleResetAck(text)
                "ERROR_ACK" -> handleErrorAck(text)
                "ANALYSIS_RESPONSE" -> handleAnalysisResponse(text)
                "ERROR" -> handleServerError(text)
                else -> Log.w(TAG, "Unknown message type: $messageType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle message: $text", e)
        }
    }

    private fun handlePong(json: String) {
        lastPongReceivedAt = System.currentTimeMillis()
        Log.d(TAG, "Received PONG")
    }

    private fun handleResetAck(json: String) {
        Log.d(TAG, "Received RESET_ACK")
    }

    private fun handleErrorAck(json: String) {
        Log.d(TAG, "Received ERROR_ACK")
    }

    private fun handleAnalysisResponse(json: String) {
        try {
            val adapter = moshi.adapter(AnalysisResponse::class.java)
            val response = adapter.fromJson(json)
            if (response != null) {
                Log.d(TAG, "Received ANALYSIS_RESPONSE")
                scope.launch {
                    try {
                        _analysisResponses.emit(response)
                    } catch (e: CancellationException) {
                        throw e  // Coroutineキャンセルは再スロー
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to emit analysis response", e)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ANALYSIS_RESPONSE", e)
        }
    }

    private fun handleServerError(json: String) {
        try {
            val adapter = moshi.adapter(ServerErrorMessage::class.java)
            val error = adapter.fromJson(json)
            if (error != null) {
                Log.e(TAG, "Server error: ${error.message}")
                _connectionState.value = ConnectionState.Error(error.message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ERROR message", e)
        }
    }

    // ========== WebSocketListener Implementation ==========

    private inner class WebSocketListenerImpl : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened")
            _connectionState.value = ConnectionState.Connected
            reconnectAttempt = 0
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received message: $text")
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            stopHeartbeat()
            if (_connectionState.value !is ConnectionState.Disconnected) {
                triggerReconnect("Connection closed: $reason")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.javaClass.simpleName} - ${t.message}", t)
            stopHeartbeat()
            if (_connectionState.value !is ConnectionState.Disconnected) {
                triggerReconnect("Connection failed: ${t.message}")
            }
        }
    }
}
