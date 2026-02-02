package com.era.android.ui

import com.era.android.data.websocket.ConnectionState
import com.era.android.domain.model.Session

/**
 * メイン画面のUI状態
 *
 * @property session 現在のセッション（nullの場合はセッションなし）
 * @property connectionState WebSocket接続状態
 * @property isLoading ローディング中フラグ
 * @property error エラーメッセージ
 */
data class MainUiState(
    val session: Session? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * セッションがアクティブかどうか
     */
    val isSessionActive: Boolean
        get() = session?.isActive == true

    /**
     * WebSocket接続中かどうか
     */
    val isConnected: Boolean
        get() = connectionState is ConnectionState.Connected

    /**
     * 再接続中かどうか
     */
    val isReconnecting: Boolean
        get() = connectionState is ConnectionState.Reconnecting

    /**
     * 接続エラーかどうか
     */
    val hasConnectionError: Boolean
        get() = connectionState is ConnectionState.Error
}
