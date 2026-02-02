package com.era.android.domain.usecase

import com.era.android.data.api.ApiResult
import com.era.android.data.repository.SessionRepository
import com.era.android.data.websocket.WebSocketClient
import com.era.android.domain.model.Session
import com.era.android.domain.model.SessionStatus
import java.time.Instant
import javax.inject.Inject

/**
 * セッション開始UseCase
 *
 * セッションを作成し、WebSocket接続を確立する
 */
class StartSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val webSocketClient: WebSocketClient
) {
    /**
     * セッションを開始
     *
     * 1. REST API でセッション作成
     * 2. WebSocket 接続を確立
     * 3. 成功/失敗を返却
     *
     * @return 成功時はSession、失敗時はエラー
     */
    suspend operator fun invoke(): Result<Session> {
        return when (val result = sessionRepository.createSession()) {
            is ApiResult.Success -> {
                val response = result.data
                val session = Session(
                    id = response.id,
                    status = SessionStatus.fromString(response.status),
                    startedAt = Instant.parse(response.startedAt),
                    endedAt = response.endedAt?.let { Instant.parse(it) }
                )
                // WebSocket接続を確立
                webSocketClient.connect(session.id)
                Result.success(session)
            }
            is ApiResult.Error -> {
                Result.failure(Exception(result.message ?: "Failed to create session"))
            }
            is ApiResult.NetworkError -> {
                Result.failure(result.throwable)
            }
        }
    }
}
