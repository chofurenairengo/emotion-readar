from __future__ import annotations

from typing import Protocol

from app.models.session import Session


class SessionRepository(Protocol):
    def save(self, session: Session) -> None: ...

    def get(self, session_id: str) -> Session | None: ...
