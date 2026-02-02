package com.commuxr.android.feature.camera

import android.Manifest
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commuxr.android.R
import com.commuxr.android.core.ui.component.RequirePermission

/**
 * カメラプレビューを表示するComposable
 *
 * @param onFrame フレームごとに呼び出されるコールバック。ImageProxy.close()は呼び出し側の責任
 * @param onCameraReady カメラが準備完了した時に呼び出されるコールバック
 * @param onError エラー発生時に呼び出されるコールバック
 * @param modifier Modifier
 */
@Composable
fun CameraScreen(
    onFrame: (ImageProxy) -> Unit,
    onCameraReady: () -> Unit = {},
    onError: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel: CameraViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    RequirePermission(
        permission = Manifest.permission.CAMERA,
        rationaleMessage = "カメラを使用して表情を解析します"
    ) {
        CameraPreviewContent(
            viewModel = viewModel,
            uiState = uiState,
            onFrame = onFrame,
            onCameraReady = {
                viewModel.onCameraReady()
                onCameraReady()
            },
            onError = { message ->
                viewModel.onError(message)
                onError(message)
            },
            modifier = modifier
        )
    }
}

@Composable
private fun CameraPreviewContent(
    viewModel: CameraViewModel,
    uiState: CameraViewModel.UiState,
    onFrame: (ImageProxy) -> Unit,
    onCameraReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember {
        CameraManager(context, lifecycleOwner)
    }

    val previewView = remember { PreviewView(context) }

    // カメラの初期化と解放
    // keyにisFrontCameraを含めない（カメラ切り替えはswitchCamera()で処理）
    DisposableEffect(cameraManager) {
        cameraManager.startCamera(previewView, onFrame, onError)
        onCameraReady()
        onDispose {
            cameraManager.release()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // カメラ切り替えボタン
        IconButton(
            onClick = {
                viewModel.switchCamera()
                cameraManager.switchCamera()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .background(Color(0x66000000), CircleShape)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_camera_switch),
                contentDescription = "カメラ切り替え",
                tint = Color.White
            )
        }

        // ステータスメッセージ
        val statusMessage = when {
            uiState.errorMessage != null -> uiState.errorMessage
            uiState.isCameraReady -> "Streaming face data"
            else -> "Starting camera"
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
