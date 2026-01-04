from pydantic import BaseModel, Field
from typing import List

# --- 入力データ定義 ---
class NonVerbalContext(BaseModel):
    smile_score: float = Field(..., description="笑顔度 0.0-1.0")
    gaze_direction: str = Field(..., description="視線方向")
    voice_tone: str = Field(..., description="声のトーン")
    # 追加: プロンプトに必要なコンテキスト
    place: str = Field("不明", description="現在地 (例: カフェ, オフィス)")
    relation: str = Field("不明", description="相手との関係 (例: 初対面, 友人)")

class AgentInput(BaseModel):
    text: str # 相手の発言
    non_verbal: NonVerbalContext
    history: List[str] = []

# --- 出力データ定義 (プロンプトのJSON例と一致させる) ---
class CoachingResponse(BaseModel):
    advice: str = Field(..., description="ユーザーへのアドバイス (30文字以内)")
    strategy_tag: str = Field(..., description="戦略タグ (例: #共感)")
    reply_candidates: List[str] = Field(..., description="返答候補リスト (各20文字以内)")
    risk_score: int = Field(..., description="会話の危険度 1-5")
    