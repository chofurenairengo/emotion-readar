package com.commuxr.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.commuxr.android.ui.theme.AndroidTheme
import com.commuxr.android.feature.camera.CameraScreen
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

import com.commuxr.android.core.ui.theme.AndroidTheme

import androidx.hilt.navigation.compose.hiltViewModel
import com.commuxr.android.data.websocket.ConnectionState
import com.commuxr.android.ui.MainUiState
import com.commuxr.android.ui.MainViewModel


import com.commuxr.android.unity.UnityMessageSender
import com.commuxr.android.vision.FaceLandmarkerAnalyzer
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
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
                val viewModel: MainViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()

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

@Composable
private fun CameraScreen(
    hasCameraPermission: Boolean,
    statusMessage: String,
    uiState: MainUiState,
    onRequestPermission: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit
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

        // ステータス表示とセッション制御
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(12.dp)
        ) {
            // 接続状態表示
            val connectionStatus = when (val state = uiState.connectionState) {
                is ConnectionState.Disconnected -> "Disconnected"
                is ConnectionState.Connecting -> "Connecting..."
                is ConnectionState.Connected -> "Connected"
                is ConnectionState.Reconnecting -> "Reconnecting (${state.attempt})..."
                is ConnectionState.Error -> "Error: ${state.message}"
            }
            Text(
                text = "$statusMessage | $connectionStatus",
                color = Color.White
            )

            // セッション情報
            uiState.session?.let { session ->
                Text(
                    text = "Session: ${session.id.take(8)}... (${session.status})",
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // エラー表示
            uiState.error?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color.Red,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // セッション制御ボタン
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                } else if (uiState.isSessionActive) {
                    Button(onClick = onEndSession) {
                        Text("End Session")
                    }
                } else {
                    Button(onClick = onStartSession) {
                        Text("Start Session")
                    }
                }
            }
        }
    }
}
