from datetime import datetime

from pydantic import BaseModel

from app.models.session import Session


class SessionResponse(BaseModel):
    id: str
    status: str
    started_at: datetime
    ended_at: datetime | None = None

    @classmethod
    def from_model(cls, session: Session) -> "SessionResponse":
        return cls(
            id=session.id,
            status=session.status,
            started_at=session.started_at,
            ended_at=session.ended_at,
        )
