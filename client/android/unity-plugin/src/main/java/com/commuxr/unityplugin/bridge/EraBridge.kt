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
import org.json.JSONObject
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
    private const val PLUGIN_BUILD_SIGNATURE = "unity-plugin-2026-02-07-facelandmarker-fix-01"
    private const val DEFAULT_UNITY_OBJECT = "EraBridgeReceiver"
    private const val DEFAULT_UNITY_METHOD = "OnBridgeMessage"
    private const val MIN_SEND_INTERVAL_MS = 400L
    private const val CAMERA_MODE_INTERNAL = "internal"
    private const val CAMERA_MODE_EXTERNAL = "external"

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
        start(sessionId, wsHost, CAMERA_MODE_INTERNAL, "")
    }

    @JvmStatic
    fun start(sessionId: String, wsHost: String, cameraMode: String) {
        start(sessionId, wsHost, cameraMode, "")
    }

    /**
     * cameraMode:
     * - "internal": 端末内蔵CameraXを利用
     * - "external": 内蔵カメラを使わず、submitExternalEmotionScores()からの入力のみ利用
     * token:
     * - Firebase ID Token（DEV_AUTH_BYPASS=false環境で必須）
     */
    @JvmStatic
    fun start(sessionId: String, wsHost: String, cameraMode: String, token: String) {
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

            val appContext = activity.applicationContext
            val normalizedHost = normalizeHost(wsHost)
            val normalizedCameraMode = normalizeCameraMode(cameraMode)
            val bridgeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope = bridgeScope

            val wsClient = WebSocketClient(baseUrl = normalizedHost, scope = bridgeScope)
            webSocketClient = wsClient

            Log.i(TAG, "Plugin build signature: $PLUGIN_BUILD_SIGNATURE")
            if (normalizedCameraMode == CAMERA_MODE_INTERNAL) {
                if (!hasCameraPermission(activity)) {
                    Log.e(TAG, "CAMERA permission is missing")
                    sendBridgeMessage("ERROR", "CAMERA permission missing")
                    try { wsClient.close() } catch (t: Throwable) { Log.w(TAG, "cleanup error", t) }
                    bridgeScope.cancel()
                    webSocketClient = null
                    scope = null
                    return
                }

                val landmarker = FaceLandmarkerHelper(
                    context = appContext,
                    listener = object : FaceLandmarkerHelper.Listener {
                        override fun onResults(emotionScores: EmotionScores, timestampMs: Long) {
                            sendAnalysisRequestInternal(wsClient, emotionScores.toMap(), timestampMs)
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
            } else {
                sendBridgeMessage("INFO", "External camera mode enabled (internal camera disabled)")
            }

            callbackCollectorJob = bridgeScope.launch {
                wsClient.analysisResponses.collect {
                    sendBridgeMessage("ANALYSIS_RESPONSE", "received")
                }
            }

            wsClient.connect(sessionId, token)
            running.set(true)
            sendBridgeMessage("INFO", "EraBridge started")
            Log.i(TAG, "Started. sessionId=$sessionId host=$normalizedHost cameraMode=$normalizedCameraMode")
        }
    }

    /**
     * 外部カメラ側で算出した感情スコアJSONを受け取り、ANALYSIS_REQUESTとして送信する。
     * 例:
     * {"happy":0.7,"sad":0.1,"angry":0.0,"confused":0.2,"surprised":0.0,"fearful":0.0,"disgusted":0.0}
     */
    @JvmStatic
    fun submitExternalEmotionScores(emotionScoresJson: String) {
        val wsClient = synchronized(lock) { webSocketClient } ?: run {
            Log.w(TAG, "submitExternalEmotionScores ignored: bridge not started")
            return
        }

        if (emotionScoresJson.isBlank()) {
            Log.w(TAG, "submitExternalEmotionScores ignored: empty payload")
            return
        }

        try {
            val json = JSONObject(emotionScoresJson)
            val emotionMap = linkedMapOf(
                "happy" to json.optDouble("happy", 0.0).toFloat(),
                "sad" to json.optDouble("sad", 0.0).toFloat(),
                "angry" to json.optDouble("angry", 0.0).toFloat(),
                "confused" to json.optDouble("confused", 0.0).toFloat(),
                "surprised" to json.optDouble("surprised", 0.0).toFloat(),
                "fearful" to json.optDouble("fearful", 0.0).toFloat(),
                "disgusted" to json.optDouble("disgusted", 0.0).toFloat(),
            )
            sendAnalysisRequestInternal(wsClient, emotionMap, System.currentTimeMillis())
        } catch (t: Throwable) {
            Log.e(TAG, "submitExternalEmotionScores parse failed", t)
            sendBridgeMessage("ERROR", "Invalid external emotion scores JSON")
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

    private fun normalizeCameraMode(raw: String): String {
        return when (raw.trim().lowercase()) {
            CAMERA_MODE_EXTERNAL -> CAMERA_MODE_EXTERNAL
            else -> CAMERA_MODE_INTERNAL
        }
    }

    private fun sendAnalysisRequestInternal(
        wsClient: WebSocketClient,
        emotionMap: Map<String, Float>,
        timestampMs: Long,
    ) {
        if (!running.get()) return

        val now = System.currentTimeMillis()
        if (now - lastSentAtMs < MIN_SEND_INTERVAL_MS) return
        lastSentAtMs = now

        if (wsClient.connectionState.value is ConnectionState.Connected) {
            val topEmotion = emotionMap.maxByOrNull { it.value }
            Log.d(
                TAG,
                "Sending ANALYSIS_REQUEST at=$timestampMs top=${topEmotion?.key}:${topEmotion?.value}",
            )
            wsClient.sendAnalysisRequest(emotionMap)
        } else {
            Log.d(TAG, "Skip ANALYSIS_REQUEST: websocket not connected")
        }
    }

    private fun sendBridgeMessage(type: String, message: String) {
        val sender = callbackSender ?: UnityMessageSender(DEFAULT_UNITY_OBJECT, DEFAULT_UNITY_METHOD)
        val payload = JSONObject()
            .put("type", type)
            .put("message", message)
        sender.send(payload.toString())
    }
}
