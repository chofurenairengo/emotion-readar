"""感情解釈サービスの実装."""

from app.dto.emotion import EmotionChange, EmotionInterpretation

# 感情名 → 日本語説明のマッピング
EMOTION_DESCRIPTIONS: dict[str, dict[str, str]] = {
    "happy": {
        "low": "相手は少し嬉しそうです",
        "medium": "相手は嬉しそうにしています",
        "high": "相手はとても喜んでいます",
    },
    "sad": {
        "low": "相手は少し寂しそうです",
        "medium": "相手は悲しんでいるようです",
        "high": "相手はとても悲しんでいます",
    },
    "angry": {
        "low": "相手は少し不満がありそうです",
        "medium": "相手は怒っているようです",
        "high": "相手はとても怒っています",
    },
    "surprised": {
        "low": "相手は少し驚いています",
        "medium": "相手は驚いているようです",
        "high": "相手はとても驚いています",
    },
    "confused": {
        "low": "相手は少し戸惑っています",
        "medium": "相手は困惑しているようです",
        "high": "相手はとても困惑しています",
    },
    "neutral": {
        "low": "相手は落ち着いています",
        "medium": "相手は平静な状態です",
        "high": "相手は無表情です",
    },
    "fearful": {
        "low": "相手は少し不安そうです",
        "medium": "相手は怖がっているようです",
        "high": "相手はとても怖がっています",
    },
    "disgusted": {
        "low": "相手は少し不快そうです",
        "medium": "相手は嫌悪感を示しています",
        "high": "相手は強い嫌悪感を示しています",
    },
}

# 感情に応じた行動提案
EMOTION_SUGGESTIONS: dict[str, str | None] = {
    "happy": "この調子で会話を続けると良いでしょう",
    "sad": "共感を示すと良いかもしれません",
    "angry": "一度話を整理して、相手の意見を聞いてみましょう",
    "surprised": "追加の説明をすると良いかもしれません",
    "confused": "説明を補足すると良いかもしれません",
    "neutral": None,
    "fearful": "安心させる言葉をかけると良いでしょう",
    "disgusted": "話題を変えることを検討してください",
}

# 感情変化の説明マッピング
EMOTION_CHANGE_DESCRIPTIONS: dict[str, dict[str, str]] = {
    "happy": {
        "sad": "相手の表情が曇りました",
        "angry": "相手の機嫌が悪くなったようです",
        "confused": "相手が困惑し始めました",
        "fearful": "相手が不安そうになりました",
        "disgusted": "相手が不快感を示し始めました",
        "neutral": "相手が落ち着いた様子になりました",
        "surprised": "相手が驚いた様子になりました",
    },
    "sad": {
        "happy": "相手の表情が明るくなりました",
        "angry": "相手が怒り始めたようです",
        "confused": "相手が困惑し始めました",
        "fearful": "相手がさらに不安そうになりました",
        "disgusted": "相手が不快感を示し始めました",
        "neutral": "相手が落ち着いた様子になりました",
        "surprised": "相手が驚いた様子になりました",
    },
    "angry": {
        "happy": "相手の機嫌が良くなりました",
        "sad": "相手が悲しそうになりました",
        "confused": "相手が困惑し始めました",
        "fearful": "相手が怖がり始めました",
        "disgusted": "相手が嫌悪感を示し始めました",
        "neutral": "相手が落ち着いた様子になりました",
        "surprised": "相手が驚いた様子になりました",
    },
    "confused": {
        "happy": "相手が理解して嬉しそうになりました",
        "sad": "相手が悲しそうになりました",
        "angry": "相手が怒り始めたようです",
        "fearful": "相手が不安そうになりました",
        "disgusted": "相手が不快感を示し始めました",
        "neutral": "相手が落ち着いた様子になりました",
        "surprised": "相手が驚いた様子になりました",
    },
    "neutral": {
        "happy": "相手の表情が明るくなりました",
        "sad": "相手の表情が曇りました",
        "angry": "相手が怒り始めたようです",
        "confused": "相手が困惑し始めました",
        "fearful": "相手が不安そうになりました",
        "disgusted": "相手が不快感を示し始めました",
        "surprised": "相手が驚いた様子になりました",
    },
    "fearful": {
        "happy": "相手が安心して嬉しそうになりました",
        "sad": "相手が悲しそうになりました",
        "angry": "相手が怒り始めたようです",
        "confused": "相手が困惑し始めました",
        "disgusted": "相手が不快感を示し始めました",
        "neutral": "相手が落ち着いた様子になりました",
        "surprised": "相手が驚いた様子になりました",
    },
    "disgusted": {
        "happy": "相手の表情が明るくなりました",
        "sad": "相手が悲しそうになりました",
        "angry": "相手が怒り始めたようです",
        "confused": "相手が困惑し始めました",
        "fearful": "相手が不安そうになりました",
        "neutral": "相手が落ち着いた様子になりました",
        "surprised": "相手が驚いた様子になりました",
    },
    "surprised": {
        "happy": "相手が嬉しそうになりました",
        "sad": "相手が悲しそうになりました",
        "angry": "相手が怒り始めたようです",
        "confused": "相手が困惑し始めました",
        "fearful": "相手が不安そうになりました",
        "disgusted": "相手が不快感を示し始めました",
        "neutral": "相手が落ち着いた様子になりました",
    },
}


class EmotionInterpreterService:
    """感情スコアを人間が理解できる形に変換."""

    def interpret(
        self,
        emotion_scores: dict[str, float],
    ) -> EmotionInterpretation:
        """感情スコアを解釈.

        Args:
            emotion_scores: 感情スコア辞書
                例: {"happy": 0.1, "confused": 0.8, "neutral": 0.1}

        Returns:
            EmotionInterpretation:
                primary_emotion: "confused"
                intensity: "high"
                description: "相手は困惑しているようです"
                suggestion: "説明を補足すると良いかもしれません"
        """
        primary_emotion = self._get_primary_emotion(emotion_scores)
        score = emotion_scores[primary_emotion]
        intensity = self._calculate_intensity(score)
        description = self._get_description(primary_emotion, intensity)
        suggestion = EMOTION_SUGGESTIONS.get(primary_emotion)

        return EmotionInterpretation(
            primary_emotion=primary_emotion,
            intensity=intensity,
            description=description,
            suggestion=suggestion,
        )

    def detect_change(
        self,
        previous: dict[str, float],
        current: dict[str, float],
        threshold: float = 0.3,
    ) -> EmotionChange | None:
        """感情の急激な変化を検出.

        Args:
            previous: 前回の感情スコア
            current: 現在の感情スコア
            threshold: 変化検出の閾値

        Returns:
            変化が検出された場合はEmotionChange、なければNone
        """
        prev_primary = self._get_primary_emotion(previous)
        curr_primary = self._get_primary_emotion(current)

        if prev_primary == curr_primary:
            return None

        prev_score = previous.get(prev_primary, 0.0)
        curr_score = current.get(curr_primary, 0.0)

        if abs(curr_score - prev_score) < threshold:
            prev_curr_score = current.get(prev_primary, 0.0)
            if abs(prev_score - prev_curr_score) < threshold:
                return None

        description = self._get_change_description(prev_primary, curr_primary)

        return EmotionChange(
            from_emotion=prev_primary,
            to_emotion=curr_primary,
            description=description,
        )

    def _get_primary_emotion(self, emotion_scores: dict[str, float]) -> str:
        """スコアが最も高い感情を取得."""
        return max(emotion_scores, key=lambda k: emotion_scores[k])

    def _calculate_intensity(self, score: float) -> str:
        """スコアから強度を計算.

        閾値:
            - low: 0.0 <= score < 0.4
            - medium: 0.4 <= score < 0.7
            - high: 0.7 <= score <= 1.0
        """
        if score < 0.4:
            return "low"
        elif score < 0.7:
            return "medium"
        else:
            return "high"

    def _get_description(self, emotion: str, intensity: str) -> str:
        """感情と強度から説明文を取得."""
        emotion_desc = EMOTION_DESCRIPTIONS.get(emotion)
        if emotion_desc is None:
            return f"相手は{emotion}の状態です"
        return emotion_desc.get(intensity, f"相手は{emotion}の状態です")

    def _get_change_description(self, from_emotion: str, to_emotion: str) -> str:
        """感情変化の説明を取得."""
        from_desc = EMOTION_CHANGE_DESCRIPTIONS.get(from_emotion)
        if from_desc is None:
            return f"相手の感情が{from_emotion}から{to_emotion}に変化しました"
        return from_desc.get(
            to_emotion, f"相手の感情が{from_emotion}から{to_emotion}に変化しました"
        )
