package com.commuxr.unityplugin.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.commuxr.unityplugin.feature.vision.EmotionScoreCalculator
import com.commuxr.unityplugin.feature.vision.EmotionScores
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.io.Closeable

/**
 * MediaPipe Face Landmarkerのラッパークラス
 *
 * Listenerパターンで結果とエラーを通知する
 */
class FaceLandmarkerHelper(
    private val context: Context,
    private val listener: Listener,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET,
    private val delegate: Delegate = Delegate.CPU,
    private val runningMode: RunningMode = RunningMode.LIVE_STREAM,
) : Closeable {

    /**
     * 結果とエラーを受け取るリスナー
     */
    interface Listener {
        /**
         * 感情スコア算出完了時に呼び出される
         * @param emotionScores 算出された感情スコア
         * @param timestampMs 検出時のタイムスタンプ（ミリ秒）
         */
        fun onResults(emotionScores: EmotionScores, timestampMs: Long)

        /**
         * エラー発生時に呼び出される
         * @param error エラーメッセージ
         */
        fun onError(error: String)
    }

    private var faceLandmarker: FaceLandmarker? = null

    /**
     * FaceLandmarkerを初期化
     * アクティビティのonCreate等で呼び出す
     */
    fun setup() {
        try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setDelegate(delegate)
                        .setModelAssetPath(modelAssetPath)
                        .build()
                )
                .setRunningMode(runningMode)
                .setOutputFaceBlendshapes(true)
                .setResultListener { result, _ -> handleResult(result) }
                .setErrorListener { error -> listener.onError(error.message ?: "FaceLandmarker error") }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            listener.onError("Failed to initialize FaceLandmarker: ${e.message}")
        }
    }

    /**
     * ImageProxyから顔検出を実行
     * CameraManagerのonFrameコールバックから呼び出す
     *
     * @param imageProxy CameraXから取得したImageProxy
     * @param isFrontCamera フロントカメラかどうか（将来の拡張用）
     */
    fun detectAsync(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val landmarker = faceLandmarker
        if (landmarker == null) {
            listener.onError("FaceLandmarker is not initialized. Call setup() first.")
            return
        }

        var bitmap: Bitmap? = null
        try {
            bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = SystemClock.uptimeMillis()
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            listener.onError(e.message ?: "Detection error")
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * リソースを解放
     * アクティビティのonDestroy等で呼び出す
     */
    override fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    private fun handleResult(result: FaceLandmarkerResult) {
        val blendshapes = result.faceBlendshapes().orElse(null)
        if (blendshapes.isNullOrEmpty()) return

        val categories = blendshapes.firstOrNull().orEmpty()
        val emotionScores = EmotionScoreCalculator.calculate(categories)
        val timestampMs = result.timestampMs()

        listener.onResults(emotionScores, timestampMs)
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        buffer.rewind()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    companion object {
        private const val DEFAULT_MODEL_ASSET = "face_landmarker.task"
        const val NUM_FACES = 1
        const val MIN_DETECTION_CONFIDENCE = 0.5f
        const val MIN_TRACKING_CONFIDENCE = 0.5f
        const val MIN_PRESENCE_CONFIDENCE = 0.5f
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
    }
}
