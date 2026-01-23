"""LLM応答関連のDTO定義."""

from pydantic import BaseModel, field_validator


class ResponseSuggestion(BaseModel):
    """応答候補."""

    text: str
    """応答文."""

    tone: str
    """トーン ("formal", "casual", "empathetic")."""

    intent: str
    """意図（例: "話題を深める", "共感を示す", "質問する"）."""


class LLMResponseResult(BaseModel):
    """LLM推論結果."""

    responses: list[ResponseSuggestion]
    """応答候補（2パターン）."""

    situation_analysis: str
    """状況分析（例: "相手は説明を求めています"）."""

    @field_validator("responses")
    @classmethod
    def validate_different_intents(
        cls, v: list[ResponseSuggestion]
    ) -> list[ResponseSuggestion]:
        """2つの応答が異なる意図であることを検証."""
        if len(v) != 2:
            raise ValueError("応答は必ず2パターン必要です")
        if v[0].intent == v[1].intent:
            raise ValueError("2つの応答は異なる意図である必要があります")
        return v
