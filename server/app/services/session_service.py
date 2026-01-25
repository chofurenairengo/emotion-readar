from __future__ import annotations

import uuid
from datetime import datetime, timezone

from app.core.exceptions import SessionPermissionError
from app.core.interfaces.session_repo import SessionRepository
from app.models.session import Session

SESSION_STATUS_ACTIVE = "active"
SESSION_STATUS_ENDED = "ended"


class SessionService:
    def __init__(self, repository: SessionRepository) -> None:
        self._repository = repository

    def create_session(self, owner_id: str) -> Session:
        session_id = str(uuid.uuid4())
        session = Session(
            id=session_id,
            owner_id=owner_id,
            status=SESSION_STATUS_ACTIVE,
            started_at=datetime.now(timezone.utc),
            ended_at=None,
        )
        self._repository.save(session)
        return session

    def get_session(self, session_id: str) -> Session | None:
        return self._repository.get(session_id)

    def verify_owner(self, session_id: str, user_id: str) -> Session:
        """セッションの所有者を検証する.

        Args:
            session_id: セッションID
            user_id: 検証するユーザーID（Firebase UID）

        Returns:
            Session: 検証成功時のセッション

        Raises:
            LookupError: セッションが見つからない場合
            SessionPermissionError: 所有者でない場合
        """
        session = self._repository.get(session_id)
        if session is None:
            raise LookupError("session_not_found")
        if session.owner_id != user_id:
            raise SessionPermissionError(
                "You don't have permission to access this session"
            )
        return session

    def end_session(self, session_id: str) -> Session | None:
        session = self._repository.get(session_id)
        if session is None:
            return None
        if session.status != SESSION_STATUS_ENDED:
            session.status = SESSION_STATUS_ENDED
            session.ended_at = datetime.now(timezone.utc)
            self._repository.save(session)
        return session
