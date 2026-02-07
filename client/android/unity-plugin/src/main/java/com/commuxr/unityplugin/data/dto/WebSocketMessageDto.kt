package com.commuxr.unityplugin.data.dto

import com.squareup.moshi.JsonClass

/**
 * PING メッセージ
 */
@JsonClass(generateAdapter = true)
data class PingMessage(
    val type: String = "PING"
)

/**
 * PONG メッセージ（サーバーからの応答）
 */
@JsonClass(generateAdapter = true)
data class PongMessage(
    val type: String,
    val timestamp: String
)

/**
 * RESET メッセージ
 */
@JsonClass(generateAdapter = true)
data class ResetMessage(
    val type: String = "RESET"
)

/**
 * RESET_ACK メッセージ（サーバーからの応答）
 */
@JsonClass(generateAdapter = true)
data class ResetAckMessage(
    val type: String,
    val timestamp: String
)

/**
 * ERROR_REPORT メッセージ
 */
@JsonClass(generateAdapter = true)
data class ErrorReportMessage(
    val type: String = "ERROR_REPORT",
    val message: String
)

/**
 * ERROR_ACK メッセージ（サーバーからの応答）
 */
@JsonClass(generateAdapter = true)
data class ErrorAckMessage(
    val type: String,
    val timestamp: String
)

/**
 * ERROR メッセージ（サーバーからのエラー通知）
 */
@JsonClass(generateAdapter = true)
data class ServerErrorMessage(
    val type: String,
    val message: String,
    val detail: String?,
    val timestamp: String
)

/**
 * メッセージの type フィールドのみを含む内部用DTO
 *
 * メッセージタイプ判定用に使用
 */
@JsonClass(generateAdapter = true)
data class MessageTypeDto(
    val type: String
)
