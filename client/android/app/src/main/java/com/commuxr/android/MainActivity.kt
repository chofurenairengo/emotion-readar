package com.commuxr.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.commuxr.android.ui.theme.AndroidTheme
import com.commuxr.android.feature.camera.CameraScreen
import com.commuxr.android.unity.UnityMessageSender
import com.commuxr.android.vision.FaceLandmarkerAnalyzer

class MainActivity : ComponentActivity() {
    private val unityMessageSender = UnityMessageSender(UNITY_GAME_OBJECT, UNITY_METHOD)
    private var faceLandmarkerAnalyzer: FaceLandmarkerAnalyzer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val analyzer = FaceLandmarkerAnalyzer(
            context = this,
            unityMessageSender = unityMessageSender,
            onError = { /* エラーはCameraViewModelで管理 */ },
        )
        faceLandmarkerAnalyzer = analyzer

        setContent {
            AndroidTheme {
                CameraScreen(
                    onFrame = { imageProxy ->
                        analyzer.analyze(imageProxy)
                    },
                    onError = { message ->
                        // FaceLandmarkerAnalyzerからのエラーはCameraViewModelに伝播
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceLandmarkerAnalyzer?.close()
    }

    companion object {
        private const val UNITY_GAME_OBJECT = "FaceReceiver"
        private const val UNITY_METHOD = "OnFaceData"
    }
}
