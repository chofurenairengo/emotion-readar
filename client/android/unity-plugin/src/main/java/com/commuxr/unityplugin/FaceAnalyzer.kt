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
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFaceDataDetected: (String) -> Unit
) {
    private var faceLandmarker: FaceLandmarker? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastProcessedMs: Long = 0
    private val processIntervalMs: Long = 1000 

    companion object {
        private const val TAG = "FaceAnalyzer"
    }

    fun start() {
        setupFaceLandmarker()
        startCamera()
    }

    fun stop() {
        cameraExecutor.execute {
            try {
                faceLandmarker?.close()
            } catch (e: Exception) {}
            faceLandmarker = null
        }
        cameraExecutor.shutdown()
    }

    private fun setupFaceLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .setDelegate(Delegate.CPU)
                .build()

            val options = FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "FaceLandmarker initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val now = SystemClock.elapsedRealtime()
                
                // ★ 1秒おきにログを出して、解析が動いているか確認
                if (now - lastProcessedMs >= processIntervalMs) {
                    Log.d(TAG, "Analysis loop running...")
                }

                if (now - lastProcessedMs < processIntervalMs || faceLandmarker == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {
                    lastProcessedMs = now
                    val bitmap = imageProxy.toBitmap()
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    
                    val options = ImageProcessingOptions.builder()
                        .setRotationDegrees(rotation)
                        .build()

                    val startTime = SystemClock.elapsedRealtime()
                    val result = faceLandmarker?.detect(mpImage, options)
                    val duration = SystemClock.elapsedRealtime() - startTime
                    
                    result?.let { res ->
                        val faceCount = res.faceLandmarks().size
                        // ★ 解析結果を毎回ログに出す
                        Log.d(TAG, "Result: Faces=$faceCount, Time=${duration}ms, Rot=$rotation")

                        if (faceCount > 0 && res.faceBlendshapes().isPresent) {
                            val shapes = res.faceBlendshapes().get()[0]
                            val smileL = shapes.find { it.categoryName() == "mouthSmileLeft" }?.score() ?: 0f
                            val smileR = shapes.find { it.categoryName() == "mouthSmileRight" }?.score() ?: 0f
                            val smile = (smileL + smileR) / 2f
                            
                            onFaceDataDetected(PayloadFactory.createFaceLite(smile, 0f, 0f, 0f, 0f, 0f))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Analysis error", e)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                val analysisUseCase: UseCase = imageAnalysis
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, analysisUseCase)
                Log.d(TAG, "Camera bound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
