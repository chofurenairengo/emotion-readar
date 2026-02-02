package com.era.android.domain.usecase

import com.era.android.data.api.ApiResult
import com.era.android.data.repository.SessionRepository
import com.era.android.data.websocket.WebSocketClient
import com.era.android.domain.model.Session
import com.era.android.domain.model.SessionStatus
import java.time.Instant
import javax.inject.Inject

/**
 * セッション終了UseCase
 *
 * セッションを終了し、WebSocket接続を切断する
 */
class EndSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val webSocketClient: WebSocketClient
) {
    /**
     * セッションを終了
     *
     * 1. WebSocket 切断
     * 2. REST API でセッション終了
     * 3. 成功/失敗を返却
     *
     * @param sessionId 終了するセッションID（nullの場合は現在のセッション）
     * @return 成功時はSession、失敗時はエラー
     */
    suspend operator fun invoke(sessionId: String? = null): Result<Session> {
        // 先にWebSocket切断
        webSocketClient.disconnect()

        return when (val result = sessionRepository.endSession(sessionId)) {
            is ApiResult.Success -> {
                val response = result.data
                val session = Session(
                    id = response.id,
                    status = SessionStatus.fromString(response.status),
                    startedAt = Instant.parse(response.startedAt),
                    endedAt = response.endedAt?.let { Instant.parse(it) }
                )
                Result.success(session)
            }
            is ApiResult.Error -> {
                Result.failure(Exception(result.message ?: "Failed to end session"))
            }
            is ApiResult.NetworkError -> {
                Result.failure(result.throwable)
            }
        }
    }
}
