"""FirestoreベースのConversationRepository実装."""

from __future__ import annotations

from google.cloud import firestore

from app.core.interfaces.conversation_repo import ConversationRepository
from app.dto.conversation import Utterance
from app.infra.firestore_client import get_firestore_client


class FirestoreConversationRepository(ConversationRepository):
    """FirestoreベースのConversationRepository実装."""

    COLLECTION_NAME = "sessions"
    SUBCOLLECTION_NAME = "utterances"

    def __init__(self, db: firestore.Client | None = None) -> None:
        self._db = db or get_firestore_client()

    def _utterances_ref(self, session_id: str) -> firestore.CollectionReference:
        """サブコレクション参照を取得."""
        return (
            self._db.collection(self.COLLECTION_NAME)
            .document(session_id)
            .collection(self.SUBCOLLECTION_NAME)
        )

    def add_utterance(self, session_id: str, utterance: Utterance) -> None:
        """発話を追加."""
        self._utterances_ref(session_id).add(utterance.model_dump(mode="json"))

    def get_history(self, session_id: str, limit: int = 20) -> list[Utterance]:
        """会話履歴を取得（直近N件、時系列順）."""
        docs = (
            self._utterances_ref(session_id)
            .order_by("timestamp", direction=firestore.Query.DESCENDING)
            .limit(limit)
            .stream()
        )
        utterances = [Utterance(**doc.to_dict()) for doc in docs]
        return list(reversed(utterances))  # 時系列順に戻す

    def clear_history(self, session_id: str) -> None:
        """会話履歴をクリア（バッチ削除）."""
        batch = self._db.batch()
        docs = self._utterances_ref(session_id).stream()
        for doc in docs:
            batch.delete(doc.reference)
        batch.commit()

    def count(self, session_id: str) -> int:
        """発話数を取得."""
        return len(list(self._utterances_ref(session_id).stream()))
