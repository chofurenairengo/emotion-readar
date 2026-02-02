package com.era.android.core.model

data class AudioData(
    val base64Data: String,
    val format: AudioFormat,
    val durationMs: Long
)

enum class AudioFormat(val value: String) {
    WAV("wav"),
    OPUS("opus"),
    PCM("pcm")
}
