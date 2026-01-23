from dataclasses import dataclass
from datetime import datetime


@dataclass
class Session:
    id: str
    status: str
    started_at: datetime
    ended_at: datetime | None = None
