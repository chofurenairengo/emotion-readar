"""会話履歴関連のDTO定義."""

from datetime import datetime
from enum import Enum

from pydantic import BaseModel


class Speaker(str, Enum):
    """話者識別."""

    USER = "user"
    """XRデバイス装着者."""

    PARTNER = "partner"
    """会話相手."""


class EmotionContext(BaseModel):
    """発話時の感情コンテキスト."""

    primary_emotion: str
    """主要な感情 ("happy", "confused", etc.)."""

    emotion_scores: dict[str, float]
    """全感情スコア."""


class Utterance(BaseModel):
    """単一の発話."""

    speaker: Speaker
    """話者."""

    text: str
    """発話内容."""

    timestamp: datetime
    """タイムスタンプ."""

    emotion_context: EmotionContext | None = None
    """感情コンテキスト（オプション）."""
