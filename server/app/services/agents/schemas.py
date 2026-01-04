# 入出力の定義
from typing import List

from pydantic import BaseModel, Field


# --- Input (エッジから受け取るデータ) ---
class NonVerbalContext(BaseModel):
    smile_score: float = Field(..., description="笑顔度 0.0-1.0")
    gaze_direction: str = Field(..., description="視線方向")
    voice_tone: str = Field(..., description="声のトーン")
    place: str = Field("不明", description="場所")
    relation: str = Field("不明", description="関係性")

class AgentInput(BaseModel):
    text: str
    non_verbal: NonVerbalContext
    history: List[str] = []

# --- Output (LLMが生成すべきデータ) ---
class CoachingResponse(BaseModel):
    advice: str = Field(..., description="30文字以内のアドバイス")
    strategy_tag: str = Field(..., description="戦略タグ (#共感 など)")
    reply_candidates: List[str] = Field(..., description="返答候補リスト")
    risk_score: int = Field(..., description="危険度 1-5")
