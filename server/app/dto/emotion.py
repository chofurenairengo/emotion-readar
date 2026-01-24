"""感情解釈関連のDTO定義."""

from pydantic import BaseModel


class EmotionInterpretation(BaseModel):
    """感情スコアの解釈結果."""

    primary_emotion: str
    """主要な感情 ("happy", "sad", "confused", etc.)."""

    intensity: str
    """強度 ("low", "medium", "high")."""

    description: str
    """自然言語での説明（例: "相手は困惑しているようです"）."""

    suggestion: str | None = None
    """行動提案（例: "説明を補足すると良いかもしれません"）."""
