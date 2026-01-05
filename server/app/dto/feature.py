from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel

from app.models.feature_log import FeatureLog


class FeatureRequest(BaseModel):
    session_id: str | None = None
    timestamp: datetime | None = None
    facial: dict[str, float] | None = None
    gaze: dict[str, float] | None = None
    voice: dict[str, float] | None = None
    extras: dict[str, Any] | None = None


class FeatureResponse(BaseModel):
    id: str
    session_id: str | None
    received_at: datetime
    status: str

    @classmethod
    def from_model(cls, log: FeatureLog) -> "FeatureResponse":
        return cls(
            id=log.id,
            session_id=log.session_id,
            received_at=log.received_at,
            status="accepted",
        )
