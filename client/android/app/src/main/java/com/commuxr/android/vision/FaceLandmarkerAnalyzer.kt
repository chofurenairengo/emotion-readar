package com.commuxr.android.vision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.commuxr.android.unity.UnityMessageSender
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import org.json.JSONObject
import java.io.Closeable

class FaceLandmarkerAnalyzer(
    private val context: Context,
    private val unityMessageSender: UnityMessageSender,
    private val onError: (String) -> Unit,
    private val minSendIntervalMs: Long = 33L,
) : ImageAnalysis.Analyzer, Closeable {

    private var lastSentAtMs: Long = 0L

    private val faceLandmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath(MODEL_ASSET)
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { error -> onError(error.message ?: "FaceLandmarker error") }
            .setOutputFaceBlendshapes(true)
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val options = ImageProcessingOptions.builder()
                .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
                .build()
            faceLandmarker.detectAsync(mpImage, options, SystemClock.uptimeMillis())
        } catch (error: IllegalStateException) {
            onError(error.message ?: "Analyzer error")
        } finally {
            imageProxy.close()
        }
    }

    override fun close() {
        faceLandmarker.close()
    }

    private fun handleResult(result: FaceLandmarkerResult) {
        val blendshapes = result.faceBlendshapes().orElse(null)
        if (blendshapes.isNullOrEmpty()) return

        val now = SystemClock.uptimeMillis()
        if (now - lastSentAtMs < minSendIntervalMs) return
        lastSentAtMs = now

        val categories = blendshapes.firstOrNull().orEmpty()
        val mouthSmileLeft = categories
            .firstOrNull { it.categoryName() == "mouthSmileLeft" }
            ?.score() ?: 0f

        val payload = JSONObject()
        payload.put("type", "face")
        payload.put("timestampMs", now)
        payload.put("mouthSmileLeft", mouthSmileLeft)

        unityMessageSender.send(payload.toString())
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        buffer.rewind()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    companion object {
        private const val MODEL_ASSET = "face_landmarker.task"
    }
}
