"""会話履歴リポジトリインターフェース定義."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from app.dto.conversation import Utterance


class SessionNotFoundError(Exception):
    """セッションが存在しない場合の例外."""

    def __init__(self, session_id: str) -> None:
        self.session_id = session_id
        super().__init__(f"Session not found: {session_id}")


class ConversationRepository(Protocol):
    """会話履歴を管理するリポジトリのプロトコル."""

    async def add_utterance(
        self, session_id: str, utterance: "Utterance"
    ) -> None:
        """発話を追加.

        Raises:
            SessionNotFoundError: セッションが存在しない場合
        """
        ...

    async def get_history(self, session_id: str, limit: int) -> list["Utterance"]:
        """会話履歴を取得（直近N件、時系列順）.

        Raises:
            SessionNotFoundError: セッションが存在しない場合
        """
        ...

    async def clear_history(self, session_id: str) -> None:
        """会話履歴をクリア.

        Raises:
            SessionNotFoundError: セッションが存在しない場合
        """
        ...

    async def count(self, session_id: str) -> int:
        """発話数を取得.

        Raises:
            SessionNotFoundError: セッションが存在しない場合
        """
        ...
