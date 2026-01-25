package com.commuxr.android.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * セッションAPIのレスポンスDTO
 */
@JsonClass(generateAdapter = true)
data class SessionResponse(
    val id: String,
    val status: String,  // "active" | "ended"
    @Json(name = "started_at") val startedAt: String,  // ISO 8601
    @Json(name = "ended_at") val endedAt: String?  // ISO 8601 or null
)

/**
 * APIエラーレスポンスDTO
 */
@JsonClass(generateAdapter = true)
data class ErrorResponse(
    val detail: String
)
