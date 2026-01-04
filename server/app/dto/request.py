from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    text: str
    non_verbal: dict[str, Any] = Field(default_factory=dict)


# --- Agent Input (エッジ/オーケストレータから受け取る) ---
class NonVerbalContext(BaseModel):
    smile_score: float = Field(..., description="笑顔度 0.0-1.0")
    gaze_direction: str = Field(..., description="視線方向")
    voice_tone: str = Field(..., description="声のトーン")
    place: str = Field("不明", description="場所")
    relation: str = Field("不明", description="関係性")


class AgentInput(BaseModel):
    text: str
    non_verbal: NonVerbalContext
    history: list[str] = Field(default_factory=list)
