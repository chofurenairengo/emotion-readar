"""音声関連のDTO定義."""

from enum import Enum

from pydantic import BaseModel


class AudioFormat(str, Enum):
    """対応音声フォーマット."""

    WAV = "wav"
    OPUS = "opus"
    PCM = "pcm"


class TranscriptionResult(BaseModel):
    """STT結果."""

    text: str
    """認識されたテキスト."""

    confidence: float
    """信頼度 (0.0-1.0)."""

    language: str
    """検出言語 ("ja", "en")."""

    duration_ms: int
    """音声の長さ（ミリ秒）."""
