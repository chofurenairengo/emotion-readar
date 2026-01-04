from __future__ import annotations

import uuid
from datetime import datetime, timezone

from app.core.interfaces.session_repo import SessionRepository
from app.models.session import Session

SESSION_STATUS_ACTIVE = "active"
SESSION_STATUS_ENDED = "ended"

class SessionService:
    def __init__(self, repository: SessionRepository) -> None:
        self._repository = repository

    def create_session(self) -> Session:
        session_id = str(uuid.uuid4())
        session = Session(
            id=session_id,
            status=SESSION_STATUS_ACTIVE,
            started_at=datetime.now(timezone.utc),
            ended_at=None,
        )
        self._repository.save(session)
        return session

    def get_session(self, session_id: str) -> Session | None:
        return self._repository.get(session_id)

    def end_session(self, session_id: str) -> Session | None:
        session = self._repository.get(session_id)
        if session is None:
            return None
        if session.status != SESSION_STATUS_ENDED:
            session.status = SESSION_STATUS_ENDED
            session.ended_at = datetime.now(timezone.utc)
            self._repository.save(session)
        return session
