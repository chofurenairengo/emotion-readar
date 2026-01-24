package com.commuxr.unityplugin

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceAnalyzer(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFaceDataDetected: (String) -> Unit
) {
    // ActivityではなくApplicationContextを保持してリークと干渉を防ぐ
    private val appContext = context.applicationContext
    private var faceLandmarker: FaceLandmarker? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastProcessedMs: Long = 0
    private val processIntervalMs: Long = 10000 // ★ 10秒に1回（まずは安定性確認のため）

    companion object {
        private const val TAG = "FaceAnalyzer"
    }

    fun start() {
        cameraExecutor.execute {
            setupFaceLandmarker()
            // カメラ開始はメインスレッドで
            ContextCompat.getMainExecutor(appContext).execute {
                startCamera()
            }
        }
    }

    fun stop() {
        cameraExecutor.execute {
            try { faceLandmarker?.close() } catch (e: Exception) {}
            faceLandmarker = null
        }
        cameraExecutor.shutdown()
    }

    private fun setupFaceLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .setDelegate(Delegate.CPU) // GPU競合を避けるためCPU固定
                .build()

            val options = FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(appContext, options)
            Log.d(TAG, "MediaPipe Initialized")
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe Init Failed", e)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(320, 240)) // 最低限の解像度
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val now = SystemClock.elapsedRealtime()
                    
                    // 解析タイミングでないなら即座にクローズ（CPU負荷ゼロ）
                    if (now - lastProcessedMs < processIntervalMs || faceLandmarker == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    try {
                        lastProcessedMs = now
                        Log.d(TAG, "Processing Start...")

                        val bitmap = imageProxy.toBitmap() ?: return@setAnalyzer
                        val mpImage = BitmapImageBuilder(bitmap).build()
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val options = ImageProcessingOptions.builder()
                            .setRotationDegrees(rotation)
                            .build()

                        val result = faceLandmarker?.detect(mpImage, options)
                        result?.let { res ->
                            if (res.faceLandmarks().isNotEmpty() && res.faceBlendshapes().isPresent) {
                                val shapes = res.faceBlendshapes().get()[0]
                                val smileL = shapes.find { it.categoryName() == "mouthSmileLeft" }?.score() ?: 0f
                                val smileR = shapes.find { it.categoryName() == "mouthSmileRight" }?.score() ?: 0f
                                val smile = (smileL + smileR) / 2f
                                
                                Log.d(TAG, "Smile Detected: $smile")
                                
                                // 送信処理
                                val json = PayloadFactory.createFaceLite(smile, 0f, 0f, 0f, 0f, 0f)
                                onFaceDataDetected(json)
                            } else {
                                Log.d(TAG, "No Face in frame")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Processing Error", e)
                    } finally {
                        imageProxy.close()
                    }
                }

                cameraProvider.unbindAll()
                
                // Quest 3 / 一般スマホ両対応のセレクター
                val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                val analysisUseCase: UseCase = imageAnalysis
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, analysisUseCase)
                Log.d(TAG, "Camera Pipeline Ready")

            } catch (e: Exception) {
                Log.e(TAG, "Camera Setup Failed", e)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }
}
