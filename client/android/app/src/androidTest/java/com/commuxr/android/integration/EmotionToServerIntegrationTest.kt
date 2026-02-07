package com.commuxr.android.integration

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.commuxr.android.feature.vision.EmotionScoreCalculator
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * カメラ認識 → emotion_scores → サーバーリクエスト 結合テスト
 *
 * テスト用顔画像からMediaPipeでblendshapesを抽出し、
 * EmotionScoreCalculatorで感情スコアに変換し、
 * WebSocket経由でANALYSIS_REQUESTとしてMockServerに送信する。
 *
 * 前提: app/src/androidTest/assets/test_face_happy.jpg にテスト用顔画像を配置すること
 */
@RunWith(AndroidJUnit4::class)
class EmotionToServerIntegrationTest {

    private lateinit var wsHelper: WebSocketTestHelper
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val TEST_IMAGE = "test_face_happy.jpg"
        private const val MODEL_ASSET = "face_landmarker.task"
        private val EXPECTED_EMOTION_KEYS = setOf(
            "happy", "sad", "angry", "confused",
            "surprised", "neutral", "fearful", "disgusted"
        )
    }

    @Before
    fun setUp() {
        wsHelper = WebSocketTestHelper()
    }

    @After
    fun tearDown() {
        wsHelper.stop()
    }

    @Test
    fun cameraEmotionScoresAreSentAsValidAnalysisRequest() {
        // 1. テスト画像をassetsから読み込み
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = context.assets.open(TEST_IMAGE).use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
        assertNotNull("テスト画像の読み込みに失敗", bitmap)

        // 2. MediaPipe FaceLandmarkerをIMAGEモードで初期化
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath(MODEL_ASSET)
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setOutputFaceBlendshapes(true)
            .build()

        val faceLandmarker = FaceLandmarker.createFromOptions(context, options)

        // 3. 画像から顔検出 → blendshapes取得
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = faceLandmarker.detect(mpImage)

        val blendshapes = result.faceBlendshapes().orElse(null)
        assertNotNull("blendshapesが検出されなかった（顔画像を確認してください）", blendshapes)
        assertTrue("blendshapesが空", blendshapes!!.isNotEmpty())

        val categories = blendshapes.first()
        assertTrue("blendshapeカテゴリが空", categories.isNotEmpty())

        // 4. EmotionScoreCalculatorで感情スコアに変換
        val emotionScores = EmotionScoreCalculator.calculate(categories)
        val emotionMap = emotionScores.toMap()

        // 5. MockWebServerを起動してWebSocket接続
        val baseUrl = wsHelper.start()
        val client = wsHelper.createWebSocketClient(baseUrl)

        runBlocking {
            wsHelper.connectAndWait(client)
        }

        // 6. ANALYSIS_REQUESTを送信
        client.sendAnalysisRequest(emotionScores = emotionMap)

        // 7. MockServerで受信したJSONを検証
        // PINGメッセージをスキップしてANALYSIS_REQUESTを見つける
        val json = findAnalysisRequest()
        assertNotNull("ANALYSIS_REQUESTがMockServerに届かなかった", json)

        @Suppress("UNCHECKED_CAST")
        val parsed = moshi.adapter(Map::class.java).fromJson(json!!) as Map<String, Any>

        // type == "ANALYSIS_REQUEST"
        assertEquals("ANALYSIS_REQUEST", parsed["type"])

        // session_id が存在
        assertNotNull("session_idが存在しない", parsed["session_id"])

        // timestamp が存在
        assertNotNull("timestampが存在しない", parsed["timestamp"])

        // emotion_scores に8キーが存在し、値が0.0〜1.0
        @Suppress("UNCHECKED_CAST")
        val scores = parsed["emotion_scores"] as? Map<String, Double>
        assertNotNull("emotion_scoresが存在しない", scores)
        assertEquals("emotion_scoresのキー数が8でない", 8, scores!!.size)

        for (key in EXPECTED_EMOTION_KEYS) {
            assertTrue("キー '$key' が存在しない", scores.containsKey(key))
            val value = scores[key]!!
            assertTrue("$key の値が範囲外: $value", value in 0.0..1.0)
        }

        // クリーンアップ
        faceLandmarker.close()
        client.close()
    }

    @Test
    fun emotionScoresKeysMatchServerAnalysisRequestSchema() {
        // EmotionScores.toMap()のキーがサーバーDTOのemotion_scoresフィールドと一致することを検証
        val emotionMap = com.commuxr.android.feature.vision.EmotionScores(
            happy = 0.65f,
            sad = 0.05f,
            angry = 0.02f,
            confused = 0.08f,
            surprised = 0.05f,
            neutral = 0.10f,
            fearful = 0.02f,
            disgusted = 0.03f
        ).toMap()

        // サーバー側で期待される8キー
        assertEquals("キー数が8でない", 8, emotionMap.size)
        assertEquals(EXPECTED_EMOTION_KEYS, emotionMap.keys)

        // 各値が0.0〜1.0
        for ((key, value) in emotionMap) {
            assertTrue("$key の値が範囲外: $value", value in 0f..1f)
        }
    }

    /**
     * 受信メッセージからANALYSIS_REQUESTを探す（PINGなどをスキップ）
     */
    private fun findAnalysisRequest(): String? {
        repeat(10) {
            val msg = wsHelper.awaitMessage(3_000) ?: return null
            if (msg.contains("ANALYSIS_REQUEST")) {
                return msg
            }
        }
        return null
    }
}
