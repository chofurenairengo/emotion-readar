package com.commuxr.android.integration

import com.commuxr.android.data.websocket.ConnectionState
import com.commuxr.android.data.websocket.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
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

        mockWebServer.enqueue(
            MockResponse().withWebSocketUpgrade(webSocketListener)
        )

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
        return WebSocketClient(baseUrl = baseUrl, scope = scope)
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
        timeoutMs: Long = 10_000
    ) {
        client.connect(sessionId)

        withTimeout(timeoutMs) {
            // connectionStateがConnectedになるまで待つ
            client.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    return@collect
                }
            }
        }

        // MockServer側でもWebSocket接続完了を待つ
        connectionLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
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
    }
}
