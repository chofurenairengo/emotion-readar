package com.commuxr.android.integration

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.commuxr.android.core.model.AudioFormat
import com.commuxr.android.data.websocket.WebSocketClient
import com.commuxr.android.feature.audio.AudioEncoder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * マイク → base64音声データ → サーバーリクエスト 結合テスト
 *
 * 合成PCMデータ（正弦波）をAudioEncoderでWAV/Base64に変換し、
 * WebSocket経由でANALYSIS_REQUESTとしてMockServerに送信する。
 * MockServerで受信したJSONの構造とBase64データの整合性を検証する。
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class AudioToServerIntegrationTest {

    private lateinit var wsHelper: WebSocketTestHelper
    private var client: WebSocketClient? = null

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val DURATION_SECONDS = 1
        private const val FREQUENCY_HZ = 440.0
        private const val WAV_HEADER_SIZE = 44
        private const val RIFF_CHUNK_SIZE_OFFSET = 4
        private const val CHANNELS_OFFSET = 22
        private const val SAMPLE_RATE_OFFSET = 24
        private const val BITS_PER_SAMPLE_OFFSET = 34
        private const val DATA_SIZE_OFFSET = 40
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
        client?.close()
        wsHelper.stop()
    }

    @Test
    fun audioDataIsSentAsValidAnalysisRequestWithBase64Wav() {
        // 1. 合成PCMデータ生成（440Hz正弦波、1秒、16kHz mono 16bit）
        val pcmData = generateSineWavePcm(FREQUENCY_HZ, DURATION_SECONDS, SAMPLE_RATE)
        assertTrue("PCMデータが空", pcmData.isNotEmpty())

        // 2. AudioEncoderでWAV → Base64エンコード
        val base64Audio = AudioEncoder.encode(pcmData, AudioFormat.WAV)
        assertTrue("Base64文字列が空", base64Audio.isNotEmpty())

        // 3. MockWebServerを起動してWebSocket接続
        val baseUrl = wsHelper.start()
        client = wsHelper.createWebSocketClient(baseUrl)

        runBlocking {
            wsHelper.connectAndWait(client!!)
        }

        // 4. emotion_scoresと音声データを含むANALYSIS_REQUESTを送信
        val dummyEmotionScores = mapOf(
            "happy" to 0.1f,
            "sad" to 0.05f,
            "angry" to 0.02f,
            "confused" to 0.03f,
            "surprised" to 0.02f,
            "neutral" to 0.75f,
            "fearful" to 0.01f,
            "disgusted" to 0.02f
        )

        client!!.sendAnalysisRequest(
            emotionScores = dummyEmotionScores,
            audioData = base64Audio,
            audioFormat = AudioFormat.WAV.value
        )

        // 5. MockServerで受信したJSONを検証
        val json = wsHelper.awaitAnalysisRequest()
        assertNotNull("ANALYSIS_REQUESTがMockServerに届かなかった", json)

        @Suppress("UNCHECKED_CAST")
        val parsed = moshi.adapter(Map::class.java).fromJson(json!!) as Map<String, Any>

        // type == "ANALYSIS_REQUEST"
        assertEquals("ANALYSIS_REQUEST", parsed["type"])

        // session_id, timestamp が存在
        assertNotNull("session_idが存在しない", parsed["session_id"])
        assertNotNull("timestampが存在しない", parsed["timestamp"])

        // audio_data が非空のBase64文字列
        val audioData = parsed["audio_data"] as? String
        assertNotNull("audio_dataが存在しない", audioData)
        assertTrue("audio_dataが空", audioData!!.isNotEmpty())

        // audio_format == "wav"
        assertEquals("wav", parsed["audio_format"])

        // Base64デコードしてWAVヘッダーを検証
        val wavBytes = Base64.decode(audioData, Base64.NO_WRAP)
        assertTrue("WAVデータが${WAV_HEADER_SIZE}バイト未満", wavBytes.size >= WAV_HEADER_SIZE)

        // RIFF/WAVEヘッダー検証
        val riff = String(wavBytes, 0, 4)
        assertEquals("RIFF", riff)
        val wave = String(wavBytes, 8, 4)
        assertEquals("WAVE", wave)

        // サンプルレート検証 (16000Hz)
        val sampleRate = ByteBuffer.wrap(wavBytes, SAMPLE_RATE_OFFSET, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(SAMPLE_RATE, sampleRate)

        // チャンネル数検証 (mono = 1)
        val channels = ByteBuffer.wrap(wavBytes, CHANNELS_OFFSET, 2)
            .order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(1, channels.toInt())

        // ビット深度検証 (16bit)
        val bitsPerSample = ByteBuffer.wrap(wavBytes, BITS_PER_SAMPLE_OFFSET, 2)
            .order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(16, bitsPerSample.toInt())
    }

    @Test
    fun base64AudioDataRoundTripPreservesWavStructure() {
        // PCMデータ → WAV → Base64 → デコード → PCMデータ比較
        val pcmData = generateSineWavePcm(FREQUENCY_HZ, DURATION_SECONDS, SAMPLE_RATE)

        // AudioEncoder全パイプライン
        val base64Audio = AudioEncoder.encode(pcmData, AudioFormat.WAV)

        // Base64デコード
        val wavBytes = Base64.decode(base64Audio, Base64.NO_WRAP)

        // WAVヘッダーの後のPCMデータが元データと一致
        val pcmFromWav = wavBytes.copyOfRange(WAV_HEADER_SIZE, wavBytes.size)
        assertArrayEquals("PCMデータがround-tripで一致しない", pcmData, pcmFromWav)

        // データサイズフィールドが正しい
        val dataSize = ByteBuffer.wrap(wavBytes, DATA_SIZE_OFFSET, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals("WAV dataサイズが不正", pcmData.size, dataSize)

        // ChunkSize (RIFF chunk) が正しい
        val chunkSize = ByteBuffer.wrap(wavBytes, RIFF_CHUNK_SIZE_OFFSET, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals("RIFF ChunkSizeが不正", 36 + pcmData.size, chunkSize)
    }

    @Test
    fun combinedEmotionAndAudioRequestHasCorrectFormat() {
        // emotion_scoresとaudio_dataの両方を含むリクエストを検証
        val pcmData = generateSineWavePcm(FREQUENCY_HZ, DURATION_SECONDS, SAMPLE_RATE)
        val base64Audio = AudioEncoder.encode(pcmData, AudioFormat.WAV)

        val emotionScores = mapOf(
            "happy" to 0.65f,
            "sad" to 0.05f,
            "angry" to 0.02f,
            "confused" to 0.08f,
            "surprised" to 0.05f,
            "neutral" to 0.10f,
            "fearful" to 0.02f,
            "disgusted" to 0.03f
        )

        // MockWebServerを起動して接続
        val baseUrl = wsHelper.start()
        client = wsHelper.createWebSocketClient(baseUrl)

        runBlocking {
            wsHelper.connectAndWait(client!!)
        }

        client!!.sendAnalysisRequest(
            emotionScores = emotionScores,
            audioData = base64Audio,
            audioFormat = AudioFormat.WAV.value
        )

        val json = wsHelper.awaitAnalysisRequest()
        assertNotNull("ANALYSIS_REQUESTがMockServerに届かなかった", json)

        @Suppress("UNCHECKED_CAST")
        val parsed = moshi.adapter(Map::class.java).fromJson(json!!) as Map<String, Any>

        // 全フィールドが揃っていること
        assertEquals("ANALYSIS_REQUEST", parsed["type"])
        assertNotNull(parsed["session_id"])
        assertNotNull(parsed["timestamp"])

        // emotion_scores: 8キー、各値が0.0〜1.0
        @Suppress("UNCHECKED_CAST")
        val scores = parsed["emotion_scores"] as? Map<String, Double>
        assertNotNull("emotion_scoresが存在しない", scores)
        assertEquals(EXPECTED_EMOTION_KEYS, scores!!.keys)
        for ((key, value) in scores) {
            assertTrue("$key の値が範囲外: $value", value in 0.0..1.0)
        }

        // audio_data: 非空、Base64デコード可能
        val audioData = parsed["audio_data"] as? String
        assertNotNull("audio_dataが存在しない", audioData)
        assertTrue("audio_dataが空", audioData!!.isNotEmpty())

        // audio_format: "wav"
        assertEquals("wav", parsed["audio_format"])

        // server/data/test_request.json と同じ構造であることを確認
        // （type, session_id, timestamp, emotion_scores, audio_data, audio_format）
        val expectedKeys = setOf(
            "type", "session_id", "timestamp",
            "emotion_scores", "audio_data", "audio_format"
        )
        assertTrue(
            "期待されるキーが不足: ${expectedKeys - parsed.keys}",
            parsed.keys.containsAll(expectedKeys)
        )
    }

    /**
     * 正弦波PCMデータを生成（16bit mono）
     */
    private fun generateSineWavePcm(
        frequencyHz: Double,
        durationSeconds: Int,
        sampleRate: Int
    ): ByteArray {
        val numSamples = sampleRate * durationSeconds
        val buffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            val sample = (Short.MAX_VALUE * sin(2.0 * Math.PI * frequencyHz * i / sampleRate)).toInt().toShort()
            buffer.putShort(sample)
        }

        return buffer.array()
    }
}
