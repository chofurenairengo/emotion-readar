package com.commuxr.android.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commuxr.android.core.model.EmotionScores

@Composable
fun EmotionOverlay(
    emotions: EmotionScores,
    modifier: Modifier = Modifier
) {
    val topEmotion = getTopEmotion(emotions)

    Box(modifier = modifier.padding(16.dp)) {
        Text(
            text = getEmoji(topEmotion.first),
            fontSize = 48.sp
        )
    }
}

private fun getTopEmotion(emotions: EmotionScores): Pair<String, Float> {
    return listOf(
        "happy" to emotions.happy,
        "sad" to emotions.sad,
        "angry" to emotions.angry,
        "confused" to emotions.confused,
        "surprised" to emotions.surprised,
        "neutral" to emotions.neutral,
        "fearful" to emotions.fearful,
        "disgusted" to emotions.disgusted
    ).maxByOrNull { it.second } ?: ("neutral" to 0f)
}

private fun getEmoji(emotion: String): String {
    return when (emotion) {
        "happy" -> "\uD83D\uDE0A"
        "sad" -> "\uD83D\uDE22"
        "angry" -> "\uD83D\uDE20"
        "confused" -> "\uD83D\uDE15"
        "surprised" -> "\uD83D\uDE32"
        "neutral" -> "\uD83D\uDE10"
        "fearful" -> "\uD83D\uDE28"
        "disgusted" -> "\uD83E\uDD22"
        else -> "\uD83D\uDE10"
    }
}
