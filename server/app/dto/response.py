from __future__ import annotations

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str = Field(..., description="アプリケーション全体のヘルスステータス")
    model_reachable: bool = Field(..., description="バックエンドのLLMモデルに到達可能かどうか")
