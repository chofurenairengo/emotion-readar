"""EmotionInterpreterServiceのテスト."""

from app.services.emotion_interpreter import EmotionInterpreterService


class TestInterpret:
    """interpret メソッドのテスト."""

    def test_interpret_confused_high(self) -> None:
        """高い困惑スコアの解釈テスト."""
        service = EmotionInterpreterService()

        result = service.interpret(
            {
                "happy": 0.1,
                "confused": 0.8,
                "neutral": 0.1,
            }
        )

        assert result.primary_emotion == "confused"
        assert result.intensity == "high"
        assert "困惑" in result.description
        assert result.suggestion is not None

    def test_interpret_neutral(self) -> None:
        """中立状態の解釈テスト."""
        service = EmotionInterpreterService()

        result = service.interpret(
            {
                "happy": 0.1,
                "sad": 0.1,
                "neutral": 0.8,
            }
        )

        assert result.primary_emotion == "neutral"
        assert result.suggestion is None

    def test_interpret_happy_high(self) -> None:
        """高い喜びスコアの解釈テスト."""
        service = EmotionInterpreterService()

        result = service.interpret(
            {
                "happy": 0.9,
                "neutral": 0.1,
            }
        )

        assert result.primary_emotion == "happy"
        assert result.intensity == "high"
        assert "喜んで" in result.description
        assert result.suggestion == "この調子で会話を続けると良いでしょう"

    def test_interpret_sad_medium(self) -> None:
        """中程度の悲しみスコアの解釈テスト."""
        service = EmotionInterpreterService()

        result = service.interpret(
            {
                "sad": 0.5,
                "neutral": 0.3,
                "happy": 0.2,
            }
        )

        assert result.primary_emotion == "sad"
        assert result.intensity == "medium"
        assert "悲しんで" in result.description
        assert result.suggestion == "共感を示すと良いかもしれません"

    def test_interpret_angry_low(self) -> None:
        """低い怒りスコアの解釈テスト."""
        service = EmotionInterpreterService()

        result = service.interpret(
            {
                "angry": 0.35,
                "neutral": 0.33,
                "happy": 0.32,
            }
        )

        assert result.primary_emotion == "angry"
        assert result.intensity == "low"
        assert "不満" in result.description

    def test_interpret_fearful(self) -> None:
        """恐怖スコアの解釈テスト."""
        service = EmotionInterpreterService()

        result = service.interpret(
            {
                "fearful": 0.7,
                "neutral": 0.3,
            }
        )

        assert result.primary_emotion == "fearful"
        assert result.intensity == "high"
        assert result.suggestion == "安心させる言葉をかけると良いでしょう"

    def test_interpret_surprised(self) -> None:
        """驚きスコアの解釈テスト."""
        service = EmotionInterpreterService()

        result = service.interpret(
            {
                "surprised": 0.6,
                "neutral": 0.4,
            }
        )

        assert result.primary_emotion == "surprised"
        assert result.intensity == "medium"
        assert "驚いて" in result.description

    def test_interpret_disgusted(self) -> None:
        """嫌悪スコアの解釈テスト."""
        service = EmotionInterpreterService()

        result = service.interpret(
            {
                "disgusted": 0.75,
                "neutral": 0.25,
            }
        )

        assert result.primary_emotion == "disgusted"
        assert result.intensity == "high"
        assert "嫌悪感" in result.description
        assert result.suggestion == "話題を変えることを検討してください"


class TestDetectChange:
    """detect_change メソッドのテスト."""

    def test_detect_emotion_change(self) -> None:
        """感情変化検出テスト."""
        service = EmotionInterpreterService()

        previous = {"happy": 0.7, "neutral": 0.3}
        current = {"happy": 0.2, "confused": 0.7, "neutral": 0.1}

        change = service.detect_change(previous, current)

        assert change is not None
        assert change.from_emotion == "happy"
        assert change.to_emotion == "confused"

    def test_no_change_detected(self) -> None:
        """変化なしのテスト."""
        service = EmotionInterpreterService()

        previous = {"happy": 0.7, "neutral": 0.3}
        current = {"happy": 0.65, "neutral": 0.35}

        change = service.detect_change(previous, current)

        assert change is None

    def test_detect_change_with_custom_threshold(self) -> None:
        """カスタム閾値での変化検出テスト."""
        service = EmotionInterpreterService()

        previous = {"happy": 0.6, "neutral": 0.4}
        current = {"sad": 0.55, "neutral": 0.45}

        # デフォルト閾値（0.3）では検出されない場合
        change_default = service.detect_change(previous, current)

        # 低い閾値では検出される
        change_low = service.detect_change(previous, current, threshold=0.1)

        # 主要感情が変わっているので検出される
        assert change_default is not None or change_low is not None

    def test_detect_change_happy_to_sad(self) -> None:
        """喜びから悲しみへの変化検出テスト."""
        service = EmotionInterpreterService()

        previous = {"happy": 0.8, "neutral": 0.2}
        current = {"sad": 0.7, "happy": 0.2, "neutral": 0.1}

        change = service.detect_change(previous, current)

        assert change is not None
        assert change.from_emotion == "happy"
        assert change.to_emotion == "sad"
        assert "曇り" in change.description

    def test_detect_change_neutral_to_angry(self) -> None:
        """中立から怒りへの変化検出テスト."""
        service = EmotionInterpreterService()

        previous = {"neutral": 0.8, "happy": 0.2}
        current = {"angry": 0.7, "neutral": 0.3}

        change = service.detect_change(previous, current)

        assert change is not None
        assert change.from_emotion == "neutral"
        assert change.to_emotion == "angry"

    def test_same_primary_emotion_no_change(self) -> None:
        """主要感情が同じ場合は変化なし."""
        service = EmotionInterpreterService()

        previous = {"happy": 0.5, "neutral": 0.5}
        current = {"happy": 0.8, "neutral": 0.2}

        change = service.detect_change(previous, current)

        assert change is None


class TestCalculateIntensity:
    """_calculate_intensity メソッドのテスト."""

    def test_intensity_low(self) -> None:
        """低強度の判定テスト."""
        service = EmotionInterpreterService()

        assert service._calculate_intensity(0.0) == "low"
        assert service._calculate_intensity(0.2) == "low"
        assert service._calculate_intensity(0.39) == "low"

    def test_intensity_medium(self) -> None:
        """中強度の判定テスト."""
        service = EmotionInterpreterService()

        assert service._calculate_intensity(0.4) == "medium"
        assert service._calculate_intensity(0.5) == "medium"
        assert service._calculate_intensity(0.69) == "medium"

    def test_intensity_high(self) -> None:
        """高強度の判定テスト."""
        service = EmotionInterpreterService()

        assert service._calculate_intensity(0.7) == "high"
        assert service._calculate_intensity(0.85) == "high"
        assert service._calculate_intensity(1.0) == "high"


class TestGetPrimaryEmotion:
    """_get_primary_emotion メソッドのテスト."""

    def test_single_dominant_emotion(self) -> None:
        """単一の支配的感情のテスト."""
        service = EmotionInterpreterService()

        result = service._get_primary_emotion(
            {"happy": 0.9, "sad": 0.05, "neutral": 0.05}
        )

        assert result == "happy"

    def test_multiple_emotions_pick_highest(self) -> None:
        """複数感情から最高値を選択するテスト."""
        service = EmotionInterpreterService()

        result = service._get_primary_emotion(
            {"happy": 0.3, "sad": 0.35, "confused": 0.35}
        )

        # sadとconfusedが同じ値の場合、dictの順序で最初に見つかったものが返される
        assert result in ["sad", "confused"]


class TestUnknownEmotion:
    """未知の感情に対するフォールバックテスト."""

    def test_unknown_emotion_fallback(self) -> None:
        """未知の感情に対するフォールバック."""
        service = EmotionInterpreterService()

        result = service.interpret(
            {
                "unknown_emotion": 0.9,
                "happy": 0.1,
            }
        )

        assert result.primary_emotion == "unknown_emotion"
        assert "unknown_emotion" in result.description
