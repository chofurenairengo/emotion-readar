package com.era.android.data.dto

import com.squareup.moshi.JsonClass

/**
 * ヘルスチェックAPIのレスポンスDTO
 */
@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String  // "ok"
)
