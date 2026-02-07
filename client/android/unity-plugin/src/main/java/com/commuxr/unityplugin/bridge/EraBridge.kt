package com.commuxr.unityplugin.bridge

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.commuxr.unityplugin.data.websocket.ConnectionState
import com.commuxr.unityplugin.data.websocket.WebSocketClient
import com.commuxr.unityplugin.feature.camera.FaceLandmarkerHelper
import com.commuxr.unityplugin.feature.camera.HeadlessCameraAnalyzer
import com.commuxr.unityplugin.feature.vision.EmotionScores
import com.commuxr.unityplugin.unity.UnityMessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unityから呼び出すAndroidネイティブブリッジ。
 *
 * 公開IF:
 * - start(sessionId, wsHost)
 * - stop()
 */
object EraBridge {
    private const val TAG = "EraBridge"
    private const val DEFAULT_UNITY_OBJECT = "EraBridgeReceiver"
    private const val DEFAULT_UNITY_METHOD = "OnBridgeMessage"
    private const val MIN_SEND_INTERVAL_MS = 400L

    private val running = AtomicBoolean(false)
    private val lock = Any()

    private var webSocketClient: WebSocketClient? = null
    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var cameraAnalyzer: HeadlessCameraAnalyzer? = null
    private var callbackSender: UnityMessageSender? = null
    private var callbackCollectorJob: Job? = null
    private var scope: CoroutineScope? = null

    @Volatile
    private var lastSentAtMs: Long = 0L

    @JvmStatic
    fun setUnityReceiver(gameObjectName: String, methodName: String) {
        synchronized(lock) {
            callbackSender = UnityMessageSender(gameObjectName, methodName)
        }
    }

    @JvmStatic
    fun start(sessionId: String, wsHost: String) {
        synchronized(lock) {
            if (running.get()) {
                Log.w(TAG, "Already running")
                return
            }

            val activity = resolveCurrentActivity()
            if (activity == null) {
                Log.e(TAG, "Unity currentActivity is null")
                return
            }

            if (!hasCameraPermission(activity)) {
                Log.e(TAG, "CAMERA permission is missing")
                sendBridgeMessage("ERROR", "CAMERA permission missing")
                return
            }

            val appContext = activity.applicationContext
            val normalizedHost = normalizeHost(wsHost)
            val bridgeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope = bridgeScope

            val wsClient = WebSocketClient(baseUrl = normalizedHost, scope = bridgeScope)
            webSocketClient = wsClient

            val landmarker = FaceLandmarkerHelper(
                context = appContext,
                listener = object : FaceLandmarkerHelper.Listener {
                    override fun onResults(emotionScores: EmotionScores, timestampMs: Long) {
                        if (!running.get()) return

                        val now = System.currentTimeMillis()
                        if (now - lastSentAtMs < MIN_SEND_INTERVAL_MS) return
                        lastSentAtMs = now

                        if (wsClient.connectionState.value is ConnectionState.Connected) {
                            wsClient.sendAnalysisRequest(emotionScores.toMap())
                        }
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "FaceLandmarker error: $error")
                        sendBridgeMessage("ERROR", error)
                    }
                },
            )
            faceLandmarkerHelper = landmarker
            landmarker.setup()

            val analyzer = HeadlessCameraAnalyzer(appContext)
            cameraAnalyzer = analyzer
            analyzer.start(
                onFrame = { imageProxy ->
                    try {
                        landmarker.detectAsync(imageProxy, isFrontCamera = false)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Frame process error", t)
                    } finally {
                        imageProxy.close()
                    }
                },
                onStarted = { sendBridgeMessage("INFO", "Camera started") },
                onError = { err ->
                    Log.e(TAG, err)
                    sendBridgeMessage("ERROR", err)
                },
            )

            callbackCollectorJob = bridgeScope.launch {
                wsClient.analysisResponses.collect {
                    sendBridgeMessage("ANALYSIS_RESPONSE", "received")
                }
            }

            wsClient.connect(sessionId)
            running.set(true)
            sendBridgeMessage("INFO", "EraBridge started")
            Log.i(TAG, "Started. sessionId=$sessionId host=$normalizedHost")
        }
    }

    @JvmStatic
    fun stop() {
        synchronized(lock) {
            if (!running.get()) {
                return
            }

            callbackCollectorJob?.cancel()
            callbackCollectorJob = null

            cameraAnalyzer?.release()
            cameraAnalyzer = null

            faceLandmarkerHelper?.close()
            faceLandmarkerHelper = null

            webSocketClient?.close()
            webSocketClient = null

            scope?.cancel()
            scope = null

            running.set(false)
            sendBridgeMessage("INFO", "EraBridge stopped")
            Log.i(TAG, "Stopped")
        }
    }

    private fun hasCameraPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun resolveCurrentActivity(): Activity? {
        return try {
            val unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer")
            val currentActivityField = unityPlayerClass.getField("currentActivity")
            currentActivityField.get(null) as? Activity
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to resolve Unity currentActivity", t)
            null
        }
    }

    private fun normalizeHost(raw: String): String {
        val withScheme = if (raw.startsWith("ws://") || raw.startsWith("wss://")) {
            raw
        } else {
            "ws://$raw"
        }
        return if (withScheme.endsWith('/')) withScheme else "$withScheme/"
    }

    private fun sendBridgeMessage(type: String, message: String) {
        val sender = callbackSender ?: UnityMessageSender(DEFAULT_UNITY_OBJECT, DEFAULT_UNITY_METHOD)
        sender.send("{\"type\":\"$type\",\"message\":\"$message\"}")
    }
}
