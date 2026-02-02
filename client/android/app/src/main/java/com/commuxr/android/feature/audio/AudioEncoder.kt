package com.commuxr.android.feature.audio

import android.util.Base64
import com.commuxr.android.core.model.AudioFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
            AudioFormat.WAV -> pcmToWav(pcmData)
            AudioFormat.PCM -> pcmData
            AudioFormat.OPUS -> encodeOpus(pcmData)
        }
        return toBase64(formattedData)
    }

    /**
     * バイト配列をBase64エンコード
     *
     * @param data エンコードするデータ
     * @return Base64エンコードされた文字列
     */
    fun toBase64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    /**
     * PCMデータをWAV形式に変換
     *
     * WAVフォーマット仕様:
     * - RIFF header (44 bytes) + PCM data
     *
     * @param pcmData PCM生データ
     * @param sampleRate サンプルレート（デフォルト: 16000Hz）
     * @param channels チャンネル数（デフォルト: 1 = モノラル）
     * @param bitsPerSample ビット深度（デフォルト: 16bit）
     * @return WAV形式のバイト配列
     */
    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE
    ): ByteArray {
        val dataSize = pcmData.size
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

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
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

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
