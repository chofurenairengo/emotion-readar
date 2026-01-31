package com.era.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.era.android.feature.camera.CameraScreen
import com.era.android.feature.camera.FaceLandmarkerHelper
import com.era.android.feature.vision.EmotionScores
import com.era.android.ui.theme.AndroidTheme
import com.era.android.unity.UnityMessageSender
import org.json.JSONObject

class MainActivity : ComponentActivity(), FaceLandmarkerHelper.Listener {
    private val unityMessageSender = UnityMessageSender(UNITY_GAME_OBJECT, UNITY_METHOD)
    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var isFrontCamera = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val helper = FaceLandmarkerHelper(context = this, listener = this)
        helper.setup()
        faceLandmarkerHelper = helper

        setContent {
            AndroidTheme {
                CameraScreen(
                    onFrame = { imageProxy ->
                        helper.detectAsync(imageProxy, isFrontCamera)
                        imageProxy.close()
                    },
                    onError = { /* エラーはFaceLandmarkerHelper.Listenerで処理 */ }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceLandmarkerHelper?.close()
    }

    override fun onResults(emotionScores: EmotionScores, timestampMs: Long) {
        val payload = JSONObject().apply {
            put("type", "face")
            put("timestampMs", timestampMs)
            emotionScores.toMap().forEach { (key, value) ->
                put(key, value)
            }
        }
        unityMessageSender.send(payload.toString())
    }

    override fun onError(error: String) {
        // エラーはログに記録（UIへの通知はCameraViewModelで管理）
    }

    companion object {
        private const val UNITY_GAME_OBJECT = "FaceReceiver"
        private const val UNITY_METHOD = "OnFaceData"
    }
}
