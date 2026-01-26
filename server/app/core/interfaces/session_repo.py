from __future__ import annotations

from typing import Protocol

from app.models.session import Session


class SessionRepository(Protocol):
    def save(self, session: Session) -> None:
        """セッションを保存（作成・更新）"""
        ...

    def get(self, session_id: str) -> Session | None:
        """セッションを取得"""
        ...

    def delete(self, session_id: str) -> None:
        """セッションを削除"""
        ...

    def exists(self, session_id: str) -> bool:
        """セッションの存在確認"""
        ...
