package com.era.android.data.api

import com.era.android.data.dto.HealthResponse
import com.era.android.data.dto.SessionResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * ERA REST API インターフェース
 */
interface ERAApi {
    /**
     * ヘルスチェック
     * @return サーバーの稼働状況
     */
    @GET("api/health")
    suspend fun healthCheck(): Response<HealthResponse>

    /**
     * セッション作成
     * @return 作成されたセッション情報
     */
    @POST("api/sessions")
    suspend fun createSession(): Response<SessionResponse>

    /**
     * セッション終了
     * @param sessionId セッションID
     * @return 終了したセッション情報
     */
    @POST("api/sessions/{sessionId}/end")
    suspend fun endSession(
        @Path("sessionId") sessionId: String
    ): Response<SessionResponse>

    /**
     * セッション取得
     * @param sessionId セッションID
     * @return セッション情報
     */
    @GET("api/sessions/{sessionId}")
    suspend fun getSession(
        @Path("sessionId") sessionId: String
    ): Response<SessionResponse>
}
