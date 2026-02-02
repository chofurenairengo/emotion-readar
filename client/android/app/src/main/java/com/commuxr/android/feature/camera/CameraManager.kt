package com.commuxr.android.feature.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraXの初期化・ライフサイクル管理を担当するクラス
 *
 * @param context アプリケーションコンテキスト
 * @param lifecycleOwner ライフサイクルオーナー（Activity/Fragment）
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null

    /** 外カメラをデフォルトとし、利用不可の場合はフォールバック */
    private var useFrontCamera = false

    private var currentPreviewView: PreviewView? = null
    private var currentOnFrame: ((ImageProxy) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    /**
     * カメラを開始し、プレビューとフレーム解析を開始する
     *
     * @param previewView プレビューを表示するView
     * @param onFrame フレームごとに呼び出されるコールバック。ImageProxy.close()は呼び出し側の責任
     * @param onError エラー発生時のコールバック
     */
    fun startCamera(
        previewView: PreviewView,
        onFrame: (ImageProxy) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        currentPreviewView = previewView
        currentOnFrame = onFrame
        onErrorCallback = onError

        // Executorが未作成またはshutdown済みの場合は新規作成
        if (cameraExecutor == null || cameraExecutor?.isShutdown == true) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                onErrorCallback?.invoke("カメラの初期化に失敗しました: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * カメラを停止する（リソースは解放しない）
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    /**
     * リソースを完全に解放する
     */
    fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        cameraProvider = null
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val previewView = currentPreviewView ?: return
        val onFrame = currentOnFrame ?: return
        val executor = cameraExecutor ?: return

        try {
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(executor) { imageProxy ->
                        onFrame(imageProxy)
                    }
                }

            val cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
        } catch (e: IllegalArgumentException) {
            // 選択したカメラが利用できない場合（外カメラがないデバイスなど）
            onErrorCallback?.invoke("選択したカメラが利用できません")
            // フォールバック: 反対のカメラを試す
            useFrontCamera = !useFrontCamera
            try {
                val fallbackSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            onFrame(imageProxy)
                        }
                    }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, fallbackSelector, preview, imageAnalysis)
            } catch (e2: Exception) {
                onErrorCallback?.invoke("カメラの起動に失敗しました: ${e2.message}")
            }
        } catch (e: Exception) {
            onErrorCallback?.invoke("カメラエラー: ${e.message}")
        }
    }
}
