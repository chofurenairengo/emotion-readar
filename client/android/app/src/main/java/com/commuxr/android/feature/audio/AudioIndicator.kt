package com.commuxr.android.feature.audio

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 音声録音状態を表示するインジケーター
 *
 * - モニタリング中: グレーの丸（待機中）
 * - 録音中: 赤い点滅する丸 + 録音時間
 *
 * @param isMonitoring 音声モニタリング中かどうか
 * @param isRecording 録音中かどうか
 * @param durationMs 録音経過時間（ミリ秒）
 * @param modifier Modifier
 */
@Composable
fun AudioIndicator(
    isMonitoring: Boolean,
    isRecording: Boolean,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    if (!isMonitoring && !isRecording) {
        return
    }

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecording) {
            RecordingIndicator()
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            MonitoringIndicator()
            Text(
                text = "待機中",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 録音中の点滅インジケーター
 */
@Composable
private fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(12.dp)
            .alpha(alpha)
            .background(
                color = Color.Red,
                shape = CircleShape
            )
    )
}

/**
 * モニタリング中のインジケーター
 */
@Composable
private fun MonitoringIndicator() {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = Color.Gray,
                shape = CircleShape
            )
    )
}

/**
 * ミリ秒を MM:SS 形式に変換
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
