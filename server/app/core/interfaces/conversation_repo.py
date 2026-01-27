"""会話履歴リポジトリインターフェース定義."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from app.dto.conversation import Utterance


class ConversationRepository(Protocol):
    """会話履歴を管理するリポジトリのプロトコル."""

    async def add_utterance(self, session_id: str, utterance: Utterance) -> None:
        """発話を追加."""
        ...

    async def get_history(self, session_id: str, limit: int) -> list[Utterance]:
        """会話履歴を取得（直近N件、時系列順）."""
        ...

    async def clear_history(self, session_id: str) -> None:
        """会話履歴をクリア."""
        ...

    async def count(self, session_id: str) -> int:
        """発話数を取得."""
        ...
