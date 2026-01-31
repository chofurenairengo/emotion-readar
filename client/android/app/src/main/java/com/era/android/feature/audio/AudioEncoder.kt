package com.era.android.feature.audio

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 音声フォーマット
 */
enum class AudioFormat(val value: String) {
    WAV("wav"),
    OPUS("opus"),
    PCM("pcm")
}

/**
 * PCM音声データをBase64エンコードするエンコーダー
 *
 * 対応フォーマット: WAV, PCM, OPUS
 */
object AudioEncoder {

    private const val SAMPLE_RATE = 16000
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    /**
     * PCMデータを指定フォーマットでBase64エンコード
     *
     * @param pcmData PCM 16bit モノラル 16kHz の生データ
     * @param format 出力フォーマット
     * @return Base64エンコードされた文字列
     */
    fun encode(pcmData: ByteArray, format: AudioFormat): String {
        val formattedData = when (format) {
            AudioFormat.WAV -> addWavHeader(pcmData)
            AudioFormat.PCM -> pcmData
            AudioFormat.OPUS -> encodeOpus(pcmData)
        }
        return Base64.encodeToString(formattedData, Base64.NO_WRAP)
    }

    /**
     * PCMデータにWAVヘッダーを付与
     *
     * WAVフォーマット仕様:
     * - RIFF header (44 bytes) + PCM data
     * - サンプルレート: 16000Hz
     * - チャンネル: 1 (モノラル)
     * - ビット深度: 16bit
     */
    internal fun addWavHeader(pcmData: ByteArray): ByteArray {
        val dataSize = pcmData.size
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8

        val output = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize) // ChunkSize
        buffer.put("WAVE".toByteArray())

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // SubChunk1Size (PCM)
        buffer.putShort(1) // AudioFormat (1 = PCM)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())

        // data sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        output.write(buffer.array())
        output.write(pcmData)

        return output.toByteArray()
    }

    /**
     * OPUSエンコード（将来実装）
     *
     * 現時点ではPCMデータをそのまま返す。
     * OPUS対応にはネイティブライブラリ（libopus）が必要。
     */
    private fun encodeOpus(pcmData: ByteArray): ByteArray {
        // TODO: libopus NDK統合時に実装
        // 暫定的にPCMデータをそのまま返す
        return pcmData
    }
}
