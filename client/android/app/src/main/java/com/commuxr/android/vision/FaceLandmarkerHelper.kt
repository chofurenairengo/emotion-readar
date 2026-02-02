package com.commuxr.android.vision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.io.Closeable

class FaceLandmarkerHelper(
    private val context: Context,
    private val listener: Listener,
    private val minProcessIntervalMs: Long = 33L,
) : Closeable {

    interface Listener {
        fun onResults(blendshapes: List<Category>, timestampMs: Long)
        fun onNoFaceDetected(timestampMs: Long)
        fun onError(error: String)
    }

    private var lastProcessedAtMs: Long = 0L

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
            .setErrorListener { error -> listener.onError(error.message ?: "FaceLandmarker error") }
            .setOutputFaceBlendshapes(true)
            .build()
    )

    fun analyze(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val options = ImageProcessingOptions.builder()
                .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
                .build()
            faceLandmarker.detectAsync(mpImage, options, SystemClock.uptimeMillis())
        } catch (error: IllegalStateException) {
            listener.onError(error.message ?: "Analyzer error")
        } finally {
            imageProxy.close()
        }
    }

    override fun close() {
        faceLandmarker.close()
    }

    private fun handleResult(result: FaceLandmarkerResult) {
        val now = SystemClock.uptimeMillis()
        if (now - lastProcessedAtMs < minProcessIntervalMs) return
        lastProcessedAtMs = now

        val blendshapes = result.faceBlendshapes().orElse(null)
        if (blendshapes.isNullOrEmpty()) {
            listener.onNoFaceDetected(now)
            return
        }

        val categories = blendshapes.firstOrNull().orEmpty()
        if (categories.isEmpty()) {
            listener.onNoFaceDetected(now)
            return
        }

        listener.onResults(categories, now)
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        if (planes.isEmpty()) {
            throw IllegalStateException("ImageProxy has no planes")
        }
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
