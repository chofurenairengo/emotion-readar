# 感情解釈サービスの実装

## 概要

Kotlin側から送信される感情スコアを、人間が理解できる自然言語に変換するサービスを実装する。

## 期待する仕様

### ファイル

```
server/app/services/emotion_interpreter.py
```

### インターフェース

```python
from app.dto.emotion import EmotionInterpretation, EmotionChange


class EmotionInterpreterService:
    """感情スコアを人間が理解できる形に変換"""

    def interpret(
        self,
        emotion_scores: dict[str, float],
    ) -> EmotionInterpretation:
        """
        感情スコアを解釈

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
        pass

    def detect_change(
        self,
        previous: dict[str, float],
        current: dict[str, float],
        threshold: float = 0.3,
    ) -> EmotionChange | None:
        """
        感情の急激な変化を検出

        Args:
            previous: 前回の感情スコア
            current: 現在の感情スコア
            threshold: 変化検出の閾値

        Returns:
            変化が検出された場合はEmotionChange、なければNone
        """
        pass

    def _get_primary_emotion(
        self,
        emotion_scores: dict[str, float],
    ) -> tuple[str, float]:
        """
        主要な感情を特定

        Returns:
            (感情名, スコア) のタプル
        """
        pass

    def _calculate_intensity(
        self,
        score: float,
    ) -> str:
        """
        スコアから強度を計算

        Returns:
            "low", "medium", "high" のいずれか
        """
        pass
```

### 感情マッピング定義

```python
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
EMOTION_SUGGESTIONS: dict[str, str] = {
    "happy": "この調子で会話を続けると良いでしょう",
    "sad": "共感を示すと良いかもしれません",
    "angry": "一度話を整理して、相手の意見を聞いてみましょう",
    "surprised": "追加の説明をすると良いかもしれません",
    "confused": "説明を補足すると良いかもしれません",
    "neutral": None,  # 特に提案なし
    "fearful": "安心させる言葉をかけると良いでしょう",
    "disgusted": "話題を変えることを検討してください",
}
```

### 強度の判定基準

```python
def _calculate_intensity(self, score: float) -> str:
    """
    スコアから強度を計算

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
```

### 感情変化の検出ロジック

```python
def detect_change(
    self,
    previous: dict[str, float],
    current: dict[str, float],
    threshold: float = 0.3,
) -> EmotionChange | None:
    """
    急激な感情変化を検出

    検出条件:
    1. 主要感情が変わった
    2. かつ、変化量が閾値を超えている
    """
    prev_emotion, prev_score = self._get_primary_emotion(previous)
    curr_emotion, curr_score = self._get_primary_emotion(current)

    if prev_emotion != curr_emotion:
        change_magnitude = abs(curr_score - previous.get(curr_emotion, 0))
        if change_magnitude >= threshold:
            return EmotionChange(
                from_emotion=prev_emotion,
                to_emotion=curr_emotion,
                description=f"相手の表情が{EMOTION_CHANGE_DESCRIPTIONS.get((prev_emotion, curr_emotion), '変化しました')}",
            )
    return None

# 感情変化の説明マッピング
EMOTION_CHANGE_DESCRIPTIONS: dict[tuple[str, str], str] = {
    ("happy", "sad"): "曇りました",
    ("happy", "confused"): "困惑に変わりました",
    ("neutral", "happy"): "明るくなりました",
    ("neutral", "confused"): "困惑しています",
    ("angry", "neutral"): "落ち着きました",
    # ... 他の組み合わせ
}
```

## 完了条件

- [ ] `EmotionInterpreterService`クラスが実装されている
- [ ] 全感情タイプで正しく解釈できる
- [ ] 強度（low/medium/high）が正しく判定される
- [ ] 感情変化の検出が機能する
- [ ] 単体テストが作成されている
- [ ] `mypy`でエラーがない

## テストケース

```python
import pytest
from app.services.emotion_interpreter import EmotionInterpreterService


def test_interpret_confused_high():
    """高い困惑スコアの解釈テスト"""
    service = EmotionInterpreterService()

    result = service.interpret({
        "happy": 0.1,
        "confused": 0.8,
        "neutral": 0.1,
    })

    assert result.primary_emotion == "confused"
    assert result.intensity == "high"
    assert "困惑" in result.description
    assert result.suggestion is not None


def test_interpret_neutral():
    """中立状態の解釈テスト"""
    service = EmotionInterpreterService()

    result = service.interpret({
        "happy": 0.1,
        "sad": 0.1,
        "neutral": 0.8,
    })

    assert result.primary_emotion == "neutral"
    assert result.suggestion is None


def test_detect_emotion_change():
    """感情変化検出テスト"""
    service = EmotionInterpreterService()

    previous = {"happy": 0.7, "neutral": 0.3}
    current = {"happy": 0.2, "confused": 0.7, "neutral": 0.1}

    change = service.detect_change(previous, current)

    assert change is not None
    assert change.from_emotion == "happy"
    assert change.to_emotion == "confused"


def test_no_change_detected():
    """変化なしのテスト"""
    service = EmotionInterpreterService()

    previous = {"happy": 0.7, "neutral": 0.3}
    current = {"happy": 0.65, "neutral": 0.35}

    change = service.detect_change(previous, current)

    assert change is None
```

## 関連Issue

- 親Issue: サーバーサイドMVP実装（Epic）
- 依存: #1 DTO定義
