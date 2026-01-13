package com.commuxr.unityplugin

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

class FaceAnalyzer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFaceDataDetected: (String) -> Unit
) {
    private var faceLandmarker: FaceLandmarker? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastSentMs: Long = 0
    private val intervalMs: Long = 1000 // 1秒おきに調整

    companion object {
        private const val TAG = "FaceAnalyzer"
    }

    fun start() {
        setupFaceLandmarker()
        startCamera()
    }

    fun stop() {
        cameraExecutor.shutdown()
        faceLandmarker?.close()
    }

    private fun setupFaceLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")

        val optionsBuilder = FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .build()

        try {
            faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder)
            Log.d(TAG, "FaceLandmarker created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create FaceLandmarker", e)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastSentMs < intervalMs) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val rotation = imageProxy.imageInfo.rotationDegrees
            val options = ImageProcessingOptions.builder().setRotationDegrees(rotation).build()

            val result = faceLandmarker?.detect(mpImage, options)
            result?.let { res ->
                if (res.faceBlendshapes().isPresent && res.faceBlendshapes().get().isNotEmpty()) {
                    val shapes = res.faceBlendshapes().get()[0]
                    
                    // 1. 笑顔 (Smile)
                    val smileL = shapes.find { it.categoryName() == "mouthSmileLeft" }?.score() ?: 0f
                    val smileR = shapes.find { it.categoryName() == "mouthSmileRight" }?.score() ?: 0f
                    val smile = (smileL + smileR) / 2f

                    // 2. 視線 (Gaze) 推定
                    // eyeLookIn/Out を使って左右(-1~1)、Up/Downを使って上下(-1~1)
                    val lookL = shapes.find { it.categoryName() == "eyeLookInLeft" }?.score() ?: 0f
                    val lookR = shapes.find { it.categoryName() == "eyeLookInRight" }?.score() ?: 0f
                    val lookOutL = shapes.find { it.categoryName() == "eyeLookOutLeft" }?.score() ?: 0f
                    val lookOutR = shapes.find { it.categoryName() == "eyeLookOutRight" }?.score() ?: 0f
                    val gazeX = (lookL + lookR) - (lookOutL + lookOutR)

                    val lookUpL = shapes.find { it.categoryName() == "eyeLookUpLeft" }?.score() ?: 0f
                    val lookUpR = shapes.find { it.categoryName() == "eyeLookUpRight" }?.score() ?: 0f
                    val lookDownL = shapes.find { it.categoryName() == "eyeLookDownLeft" }?.score() ?: 0f
                    val lookDownR = shapes.find { it.categoryName() == "eyeLookDownRight" }?.score() ?: 0f
                    val gazeY = (lookUpL + lookUpR) - (lookDownL + lookDownR)

                    // 3. 頭部の向き (Head Pose) 簡易推定
                    // ランドマークから 鼻(1), 左耳(234), 右耳(454) 等を使って計算も可能だが、
                    // ここでは設計書に合わせ、将来の拡張を見越したダミーまたは簡易差分を入れる
                    // (※本来はPnPアルゴリズムが必要だが、まずは0固定で枠組みを作成)
                    var pitch = 0f
                    var yaw = 0f
                    var roll = 0f
                    
                    if (res.faceLandmarks().isNotEmpty()) {
                        val landmarks = res.faceLandmarks()[0]
                        // 簡易Yaw推定: 鼻(landmark 1)と両端の耳付近の距離比
                        val nose = landmarks[1]
                        val leftEye = landmarks[33]
                        val rightEye = landmarks[263]
                        yaw = ( (nose.x() - leftEye.x()) - (rightEye.x() - nose.x()) ) * 10f
                    }

                    val json = PayloadFactory.createFaceLite(smile, gazeX, gazeY, pitch, yaw, roll)
                    onFaceDataDetected(json)
                    lastSentMs = currentTime
                    Log.d(TAG, "Sent data: smile=$smile, gazeX=$gazeX, yaw=$yaw")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Process error", e)
        } finally {
            imageProxy.close()
        }
    }
}
