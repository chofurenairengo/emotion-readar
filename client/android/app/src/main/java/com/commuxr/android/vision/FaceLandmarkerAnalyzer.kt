package com.commuxr.android.vision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.commuxr.android.network.WebSocketSender
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable

class FaceLandmarkerAnalyzer(
    private val context: Context,
    private val webSocketSender: WebSocketSender,
    private val onError: (String) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {

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
        val landmarks = result.faceLandmarks().firstOrNull()

        if ((blendshapes == null || blendshapes.isEmpty()) && landmarks == null) return

        val payload = JSONObject()
        payload.put("type", "face")
        payload.put("timestampMs", SystemClock.uptimeMillis())

        val blendshapeArray = JSONArray()
        if (!blendshapes.isNullOrEmpty()) {
            val categories: List<com.google.mediapipe.tasks.components.containers.Category> =
                blendshapes.firstOrNull().orEmpty()
            categories.forEach { category ->
                val item = JSONObject()
                item.put("name", category.categoryName())
                item.put("score", category.score())
                blendshapeArray.put(item)
            }
        }
        payload.put("blendshapes", blendshapeArray)

        val landmarkArray = JSONArray()
        landmarks?.forEach { landmark ->
            val coords = JSONArray()
            coords.put(landmark.x())
            coords.put(landmark.y())
            coords.put(landmark.z())
            landmarkArray.put(coords)
        }
        payload.put("landmarks", landmarkArray)

        webSocketSender.send(payload.toString())
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
