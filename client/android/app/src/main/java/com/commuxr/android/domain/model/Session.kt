package com.commuxr.android.domain.model

import java.time.Instant

/**
 * セッションのドメインモデル
 *
 * @property id セッションID
 * @property status セッション状態
 * @property startedAt セッション開始時刻
 * @property endedAt セッション終了時刻（アクティブ時はnull）
 */
data class Session(
    val id: String,
    val status: SessionStatus,
    val startedAt: Instant,
    val endedAt: Instant?
) {
    /**
     * セッションがアクティブかどうか
     */
    val isActive: Boolean
        get() = status == SessionStatus.ACTIVE
}

/**
 * セッション状態
 */
enum class SessionStatus {
    ACTIVE,
    ENDED;

    companion object {
        /**
         * 文字列からSessionStatusに変換
         *
         * @param value 状態を表す文字列（"active" または "ended"）
         * @return 対応するSessionStatus
         * @throws IllegalArgumentException 未知の状態の場合
         */
        fun fromString(value: String): SessionStatus {
            return when (value.lowercase()) {
                "active" -> ACTIVE
                "ended" -> ENDED
                else -> throw IllegalArgumentException("Unknown session status: $value")
            }
        }
    }
}
