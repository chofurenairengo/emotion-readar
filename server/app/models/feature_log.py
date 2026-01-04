from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any


@dataclass
class FeatureLog:
    id: str
    session_id: str | None
    received_at: datetime
    client_timestamp: datetime | None
    facial: dict[str, float] | None
    gaze: dict[str, float] | None
    voice: dict[str, float] | None
    extras: dict[str, Any] | None = None
