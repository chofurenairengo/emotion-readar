"""統合処理関連のDTO定義."""

from datetime import datetime

from pydantic import BaseModel

from app.dto.audio import TranscriptionResult
from app.dto.emotion import EmotionInterpretation
from app.dto.llm import ResponseSuggestion


class AnalysisRequest(BaseModel):
    """クライアントからの解析リクエスト（WebSocket）."""

    type: str = "ANALYSIS_REQUEST"
    """メッセージタイプ."""

    session_id: str
    """セッションID."""

    timestamp: datetime
    """クライアントタイムスタンプ."""

    emotion_scores: dict[str, float]
    """感情スコア（Kotlin側で算出）."""

    audio_data: str | None = None
    """音声データ（Base64エンコード）."""

    audio_format: str | None = None
    """音声フォーマット."""


class AnalysisResponse(BaseModel):
    """解析結果レスポンス（WebSocket）."""

    type: str = "ANALYSIS_RESPONSE"
    """メッセージタイプ."""

    timestamp: datetime
    """サーバータイムスタンプ."""

    emotion: EmotionInterpretation
    """感情解釈."""

    transcription: TranscriptionResult | None = None
    """STT結果（音声があった場合）."""

    suggestions: list[ResponseSuggestion]
    """応答候補2パターン."""

    situation_analysis: str
    """状況分析."""

    processing_time_ms: int
    """処理時間."""
