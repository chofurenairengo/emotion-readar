package com.era.android.domain.usecase

import com.era.android.data.websocket.ConnectionState
import com.era.android.data.websocket.WebSocketClient
import javax.inject.Inject

/**
 * 解析データ送信UseCase
 *
 * 感情スコアと音声データをWebSocket経由で送信する
 */
class SendAnalysisUseCase @Inject constructor(
    private val webSocketClient: WebSocketClient
) {
    /**
     * 解析リクエストを送信
     *
     * @param emotionScores 感情スコアマップ
     * @param audioData Base64エンコードされた音声データ（オプション）
     * @param audioFormat 音声フォーマット（オプション）
     * @return 送信成功の場合true、接続していない場合false
     */
    operator fun invoke(
        emotionScores: Map<String, Float>,
        audioData: String? = null,
        audioFormat: String? = null
    ): Boolean {
        // 接続中でなければ送信しない
        if (webSocketClient.connectionState.value !is ConnectionState.Connected) {
            return false
        }

        webSocketClient.sendAnalysisRequest(
            emotionScores = emotionScores,
            audioData = audioData,
            audioFormat = audioFormat
        )
        return true
    }
}
