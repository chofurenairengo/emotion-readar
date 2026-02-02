from __future__ import annotations

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str
    model_reachable: bool


# --- Agent Output (LLMが生成すべきデータ) ---
class CoachingResponse(BaseModel):
    advice: str = Field(..., description="30文字以内のアドバイス")
    strategy_tag: str = Field(..., description="戦略タグ (#共感 など)")
    reply_candidates: list[str] = Field(..., description="返答候補リスト")
    risk_score: int = Field(..., description="危険度 1-5")
