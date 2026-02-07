package com.commuxr.android.integration

import com.commuxr.android.data.websocket.ConnectionState
import com.commuxr.android.data.websocket.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 結合テスト用のWebSocket MockServer ヘルパー
 *
 * MockWebServerを使ってWebSocket接続を受け付け、
 * クライアントから送信されたメッセージをキャプチャする。
 */
class WebSocketTestHelper {

    private val mockWebServer = MockWebServer()
    private val receivedMessages = LinkedBlockingQueue<String>()
    private var serverWebSocket: WebSocket? = null
    private val connectionLatch = CountDownLatch(1)
    private var clientScope: CoroutineScope? = null

    /**
     * MockWebServerを起動し、WebSocket URLを返す
     *
     * @return ws://localhost:{port}/ 形式のURL
     */
    fun start(): String {
        val webSocketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                serverWebSocket = webSocket
                connectionLatch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                receivedMessages.offer(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
        }

        // 再接続に対応するためWebSocketアップグレードレスポンスを複数エンキュー
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse().withWebSocketUpgrade(webSocketListener)
            )
        }

        mockWebServer.start()
        val port = mockWebServer.port
        return "ws://localhost:$port/"
    }

    /**
     * MockServer向けのWebSocketClientを作成
     *
     * @param baseUrl MockServerのURL
     * @return テスト用WebSocketClient
     */
    fun createWebSocketClient(baseUrl: String): WebSocketClient {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        clientScope = scope
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return WebSocketClient(baseUrl = baseUrl, scope = scope, httpClient = httpClient)
    }

    /**
     * WebSocketClientの接続を待つ
     *
     * @param client WebSocketClient
     * @param sessionId テスト用セッションID
     * @param timeoutMs タイムアウト（ミリ秒）
     */
    suspend fun connectAndWait(
        client: WebSocketClient,
        sessionId: String = "test-session-001",
        token: String = "test-token",
        timeoutMs: Long = 30_000
    ) {
        client.connect(sessionId, token)

        // connectionStateがConnectedになるまで待つ（first{}は条件を満たしたら即リターン）
        withTimeout(timeoutMs) {
            client.connectionState.first { state ->
                state is ConnectionState.Connected
            }
        }

        // MockServer側でもWebSocket接続完了を待つ
        val connected = connectionLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        require(connected) { "MockWebServer failed to receive WebSocket connection within ${timeoutMs}ms" }
    }

    /**
     * MockServerで受信したメッセージを取得
     *
     * @param timeoutMs タイムアウト（ミリ秒）
     * @return 受信したJSON文字列、タイムアウト時はnull
     */
    fun awaitMessage(timeoutMs: Long = 5_000): String? {
        return receivedMessages.poll(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /**
     * 受信メッセージからANALYSIS_REQUESTを探す（PINGなどをスキップ）
     *
     * @param maxAttempts 最大リトライ回数
     * @param timeoutMs 1メッセージあたりのタイムアウト
     * @return ANALYSIS_REQUESTのJSON文字列、見つからない場合はnull
     */
    fun awaitAnalysisRequest(maxAttempts: Int = 10, timeoutMs: Long = 3_000): String? {
        repeat(maxAttempts) {
            val msg = awaitMessage(timeoutMs) ?: return null
            if (msg.contains("ANALYSIS_REQUEST")) {
                return msg
            }
        }
        return null
    }

    /**
     * MockServerで受信した全メッセージをクリア
     */
    fun clearMessages() {
        receivedMessages.clear()
    }

    /**
     * MockServerを停止しリソースを解放
     */
    fun stop() {
        serverWebSocket?.close(1000, "Test complete")
        mockWebServer.shutdown()
        clientScope?.cancel()
        clientScope = null
    }
}
