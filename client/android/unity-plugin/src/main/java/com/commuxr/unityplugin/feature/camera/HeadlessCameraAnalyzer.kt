package com.commuxr.unityplugin.feature.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Preview表示なしでカメラフレームを解析するヘッドレスCameraXラッパー。
 */
class HeadlessCameraAnalyzer(
    private val context: Context,
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var imageAnalysis: ImageAnalysis? = null

    fun start(
        onFrame: (ImageProxy) -> Unit,
        onStarted: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        if (cameraExecutor == null || cameraExecutor?.isShutdown == true) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                try {
                    cameraProvider = cameraProviderFuture.get()
                    bindUseCase(onFrame)
                    onStarted()
                } catch (e: Exception) {
                    onError("Camera init failed: ${e.message}")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun stop() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        stop()
        imageAnalysis = null
        cameraExecutor?.shutdown()
        cameraExecutor = null
        cameraProvider = null
    }

    private fun bindUseCase(onFrame: (ImageProxy) -> Unit) {
        val provider = cameraProvider ?: return
        val executor = cameraExecutor ?: return

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analysis.setAnalyzer(executor) { imageProxy -> onFrame(imageProxy) }

        provider.unbindAll()

        try {
            provider.bindToLifecycle(
                ProcessLifecycleOwner.get(),
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis,
            )
            imageAnalysis = analysis
        } catch (_: IllegalArgumentException) {
            provider.bindToLifecycle(
                ProcessLifecycleOwner.get(),
                CameraSelector.DEFAULT_BACK_CAMERA,
                analysis,
            )
            imageAnalysis = analysis
        }
    }
}
