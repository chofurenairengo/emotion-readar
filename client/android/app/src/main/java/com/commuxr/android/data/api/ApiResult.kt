package com.commuxr.android.data.api

/**
 * API呼び出し結果を表す型安全なクラス
 */
sealed class ApiResult<out T> {
    /**
     * 成功
     * @property data レスポンスデータ
     */
    data class Success<T>(val data: T) : ApiResult<T>()

    /**
     * HTTPエラー
     * @property code HTTPステータスコード
     * @property message エラーメッセージ
     */
    data class Error(
        val code: Int?,
        val message: String
    ) : ApiResult<Nothing>()

    /**
     * ネットワークエラー
     * @property throwable 例外オブジェクト
     */
    data class NetworkError(val throwable: Throwable) : ApiResult<Nothing>()
}
