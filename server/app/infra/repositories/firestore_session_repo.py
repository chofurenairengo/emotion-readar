from __future__ import annotations

from typing import Any

from google.cloud import firestore

from app.core.interfaces.session_repo import SessionRepository
from app.infra.firestore_client import get_firestore_client
from app.models.session import Session


class FirestoreSessionRepository(SessionRepository):
    """FirestoreベースのSessionRepository実装"""

    COLLECTION_NAME = "sessions"

    def __init__(self, db: firestore.Client | None = None) -> None:
        self._db = db or get_firestore_client()
        self._collection = self._db.collection(self.COLLECTION_NAME)

    def save(self, session: Session) -> None:
        """セッションを保存"""
        self._collection.document(session.id).set(
            {
                "id": session.id,
                "owner_id": session.owner_id,
                "status": session.status,
                "started_at": session.started_at,
                "ended_at": session.ended_at,
            }
        )

    def get(self, session_id: str) -> Session | None:
        """セッションを取得"""
        doc = self._collection.document(session_id).get()
        if not doc.exists:
            return None
        data = doc.to_dict()
        if data is None:
            return None
        return self._to_session(data)

    def delete(self, session_id: str) -> None:
        """セッションを削除"""
        self._collection.document(session_id).delete()

    def exists(self, session_id: str) -> bool:
        """セッションの存在確認"""
        doc = self._collection.document(session_id).get()
        return doc.exists

    def _to_session(self, data: dict[str, Any]) -> Session:
        """Firestoreデータ → Sessionモデル変換"""
        return Session(
            id=data["id"],
            owner_id=data["owner_id"],
            status=data["status"],
            started_at=data["started_at"],
            ended_at=data.get("ended_at"),
        )
