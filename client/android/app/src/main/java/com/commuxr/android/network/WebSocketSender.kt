package com.commuxr.android.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketSender(
    private val url: String,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null

    fun connect() {
        if (webSocket != null) return
        val request = Request.Builder()
            .url(url)
            .build()
        webSocket = client.newWebSocket(request, LoggingListener())
    }

    fun send(message: String) {
        webSocket?.send(message)
    }

    fun close() {
        webSocket?.close(1000, "bye")
        webSocket = null
    }

    private class LoggingListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
        }
    }

    companion object {
        private const val TAG = "WebSocketSender"
    }
}
