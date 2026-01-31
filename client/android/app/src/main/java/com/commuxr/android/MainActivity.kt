package com.commuxr.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.commuxr.android.feature.camera.FaceLandmarkerHelper
import com.commuxr.android.feature.vision.EmotionScores
import com.commuxr.android.ui.theme.AndroidTheme
import com.commuxr.android.unity.UnityMessageSender
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), FaceLandmarkerHelper.Listener {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val unityMessageSender = UnityMessageSender(UNITY_GAME_OBJECT, UNITY_METHOD)
    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var previewView: PreviewView? = null
    private var statusMessage by mutableStateOf("Awaiting camera permission")
    private var hasCameraPermission by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (granted) {
                statusMessage = "Starting camera"
                previewView?.let { startCamera(it) }
            } else {
                statusMessage = "Camera permission denied"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasCameraPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            statusMessage = "Starting camera"
        }
        setContent {
            AndroidTheme {
                CameraScreen(
                    hasCameraPermission = hasCameraPermission,
                    statusMessage = statusMessage,
                    onRequestPermission = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onPreviewReady = { view ->
                        previewView = view
                        if (hasCameraPermission) {
                            startCamera(view)
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceLandmarkerHelper?.close()
        cameraExecutor.shutdown()
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
        statusMessage = error
    }

    private fun startCamera(view: PreviewView) {
        val helper = FaceLandmarkerHelper(context = this, listener = this)
        helper.setup()
        faceLandmarkerHelper = helper

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        helper.detectAsync(imageProxy, isFrontCamera = true)
                        imageProxy.close()
                    }
                }
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            statusMessage = "Streaming face data"
        }, ContextCompat.getMainExecutor(this))
    }

    companion object {
        private const val UNITY_GAME_OBJECT = "FaceReceiver"
        private const val UNITY_METHOD = "OnFaceData"
    }
}

@Composable
private fun CameraScreen(
    hasCameraPermission: Boolean,
    statusMessage: String,
    onRequestPermission: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(previewView) {
        onPreviewReady(previewView)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "Camera permission is required")
                Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "Grant Permission")
                }
            }
        }
        Text(
            text = statusMessage,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0x99000000))
                .padding(12.dp)
        )
    }
}
