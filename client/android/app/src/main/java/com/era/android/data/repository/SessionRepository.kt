package com.era.android.data.repository

import com.era.android.data.api.ApiClient
import com.era.android.data.api.ApiResult
import com.era.android.data.api.ERAApi
import com.era.android.data.dto.SessionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * セッション管理リポジトリ
 *
 * セッションの作成・終了・取得を行い、現在のセッション状態を管理します。
 * @property api ERA API インスタンス（テスト用にDI可能）
 */
class SessionRepository(
    private val api: ERAApi = ApiClient.api
) {
    private val _currentSession = MutableStateFlow<SessionResponse?>(null)

    /**
     * 現在のセッション
     */
    val currentSession: StateFlow<SessionResponse?> = _currentSession.asStateFlow()

    /**
     * セッションがアクティブかどうか
     */
    val isSessionActive: Boolean
        get() = _currentSession.value?.status == "active"

    /**
     * 現在のセッションID
     */
    val currentSessionId: String?
        get() = _currentSession.value?.id

    /**
     * セッションを作成
     *
     * @return セッション作成結果
     */
    suspend fun createSession(): ApiResult<SessionResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.createSession()
            if (response.isSuccessful) {
                val session = response.body()
                if (session == null) {
                    return@withContext ApiResult.Error(response.code(), "Empty response body")
                }
                _currentSession.value = session
                ApiResult.Success(session)
            } else {
                ApiResult.Error(response.code(), response.message())
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(null, "Unexpected error: ${e.message}")
        }
    }

    /**
     * セッションを終了
     *
     * @param sessionId セッションID（nullの場合は現在のセッションIDを使用）
     * @return セッション終了結果
     */
    suspend fun endSession(sessionId: String? = currentSessionId): ApiResult<SessionResponse> =
        withContext(Dispatchers.IO) {
            if (sessionId == null) {
                return@withContext ApiResult.Error(null, "No active session")
            }
            try {
                val response = api.endSession(sessionId)
                if (response.isSuccessful) {
                    val session = response.body()
                    if (session == null) {
                        return@withContext ApiResult.Error(response.code(), "Empty response body")
                    }
                    _currentSession.value = session
                    ApiResult.Success(session)
                } else {
                    ApiResult.Error(response.code(), response.message())
                }
            } catch (e: IOException) {
                ApiResult.NetworkError(e)
            } catch (e: Exception) {
                ApiResult.Error(null, "Unexpected error: ${e.message}")
            }
        }

    /**
     * セッションを取得
     *
     * @param sessionId セッションID
     * @return セッション取得結果
     */
    suspend fun getSession(sessionId: String): ApiResult<SessionResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getSession(sessionId)
                if (response.isSuccessful) {
                    val session = response.body()
                    if (session == null) {
                        return@withContext ApiResult.Error(response.code(), "Empty response body")
                    }
                    ApiResult.Success(session)
                } else {
                    ApiResult.Error(response.code(), response.message())
                }
            } catch (e: IOException) {
                ApiResult.NetworkError(e)
            } catch (e: Exception) {
                ApiResult.Error(null, "Unexpected error: ${e.message}")
            }
        }

    /**
     * ヘルスチェック
     *
     * @return ヘルスチェック結果（true: 正常, false: 異常）
     */
    suspend fun healthCheck(): ApiResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = api.healthCheck()
            if (response.isSuccessful && response.body()?.status == "ok") {
                ApiResult.Success(true)
            } else {
                ApiResult.Error(response.code(), "Server unhealthy")
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(null, "Unexpected error: ${e.message}")
        }
    }

    /**
     * セッションをクリア
     */
    fun clearSession() {
        _currentSession.value = null
    }
}
