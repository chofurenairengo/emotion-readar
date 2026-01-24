from __future__ import annotations

from typing import Protocol

from app.models.session import Session


class SessionRepository(Protocol):
    async def put_session(self, session: Session) -> None: ...
