package com.era.android.data.websocket

/**
 * WebSocket接続状態を表すsealed class
 */
sealed class ConnectionState {
    /**
     * 切断状態
     */
    object Disconnected : ConnectionState()

    /**
     * 接続試行中
     */
    object Connecting : ConnectionState()

    /**
     * 接続済み
     */
    object Connected : ConnectionState()

    /**
     * 再接続試行中
     * @property attempt 現在の試行回数（1から開始）
     * @property nextRetryMs 次回再接続までの待機時間（ミリ秒）
     */
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : ConnectionState()

    /**
     * エラー状態
     * @property message エラーメッセージ
     * @property code エラーコード（オプション）
     */
    data class Error(val message: String, val code: Int? = null) : ConnectionState()
}
