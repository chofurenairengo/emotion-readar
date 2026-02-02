package com.commuxr.android.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.commuxr.android.feature.camera.CameraManager
import com.commuxr.android.ui.component.EmotionOverlay
import com.commuxr.android.ui.component.SuggestionOverlay
import com.commuxr.android.vision.FaceLandmarkerHelper

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = context.applicationContext

    val cameraManager = remember(appContext, lifecycleOwner) {
        CameraManager(appContext, lifecycleOwner)
    }
    val faceLandmarkerHelper = remember(appContext) {
        FaceLandmarkerHelper(appContext, viewModel)
    }
    val previewView = remember(context) {
        PreviewView(context)
    }

    DisposableEffect(cameraManager, faceLandmarkerHelper) {
        cameraManager.startCamera(
            previewView = previewView,
            onFrame = { imageProxy ->
                faceLandmarkerHelper.analyze(imageProxy)
            },
            onSuccess = {
                viewModel.onCameraReady()
            },
            onError = { error ->
                viewModel.onError(error)
            }
        )

        onDispose {
            cameraManager.release()
            faceLandmarkerHelper.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        uiState.currentEmotions?.let { emotions ->
            EmotionOverlay(
                emotions = emotions,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        SuggestionOverlay(
            suggestions = uiState.suggestions,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
