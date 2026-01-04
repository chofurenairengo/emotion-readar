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
import com.commuxr.android.network.WebSocketSender
import com.commuxr.android.ui.theme.AndroidTheme
import com.commuxr.android.vision.FaceLandmarkerAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val webSocketSender = WebSocketSender(DEFAULT_WS_URL)
    private var faceLandmarkerAnalyzer: FaceLandmarkerAnalyzer? = null
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
        webSocketSender.connect()

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
        faceLandmarkerAnalyzer?.close()
        cameraExecutor.shutdown()
        webSocketSender.close()
    }

    private fun startCamera(view: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val analyzer = FaceLandmarkerAnalyzer(
                context = this,
                webSocketSender = webSocketSender,
                onError = { message -> statusMessage = message },
            )
            faceLandmarkerAnalyzer = analyzer
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            statusMessage = "Streaming face data"
        }, ContextCompat.getMainExecutor(this))
    }

    companion object {
        private const val DEFAULT_WS_URL = "ws://10.0.2.2:8765"
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
