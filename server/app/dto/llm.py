"""LLM応答関連のDTO定義."""

from pydantic import BaseModel


class ResponseSuggestion(BaseModel):
    """応答候補（1パターン）."""

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
