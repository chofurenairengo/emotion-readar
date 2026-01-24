"""会話履歴管理サービス."""

from __future__ import annotations

import logging
import threading
from datetime import datetime, timezone

from app.dto.conversation import EmotionContext, Speaker, Utterance

logger = logging.getLogger(__name__)


class ConversationService:
    """会話履歴管理サービス."""

    def __init__(self, max_history_per_session: int = 100) -> None:
        """
        初期化.

        Args:
            max_history_per_session: セッションあたりの最大履歴数
        """
        self._histories: dict[str, list[Utterance]] = {}
        self._max_history = max_history_per_session
        self._lock = threading.Lock()

    def add_utterance(
        self,
        session_id: str,
        speaker: Speaker,
        text: str,
        emotion_context: EmotionContext | None = None,
        timestamp: datetime | None = None,
    ) -> Utterance:
        """
        発話を履歴に追加.

        Args:
            session_id: セッションID
            speaker: 話者 (USER or PARTNER)
            text: 発話内容
            emotion_context: 発話時の感情コンテキスト（オプション）
            timestamp: タイムスタンプ（省略時は現在時刻）

        Returns:
            追加されたUtteranceオブジェクト
        """
        if timestamp is None:
            timestamp = datetime.now(timezone.utc)

        utterance = Utterance(
            speaker=speaker,
            text=text,
            timestamp=timestamp,
            emotion_context=emotion_context,
        )

        with self._lock:
            if session_id not in self._histories:
                self._histories[session_id] = []

            history = self._histories[session_id]
            history.append(utterance)

            # 履歴上限を超えた場合、古いものから削除
            if len(history) > self._max_history:
                removed_count = len(history) - self._max_history
                self._histories[session_id] = history[-self._max_history :]
                logger.info(
                    "Session %s: Removed %d old utterances (limit: %d)",
                    session_id,
                    removed_count,
                    self._max_history,
                )

        return utterance

    def get_recent_context(
        self,
        session_id: str,
        max_turns: int = 10,
    ) -> list[Utterance]:
        """
        直近の会話履歴を取得.

        Args:
            session_id: セッションID
            max_turns: 取得する最大ターン数

        Returns:
            直近の発話リスト（古い順）
        """
        with self._lock:
            history = self._histories.get(session_id, [])
            return list(history[-max_turns:])

    def get_last_utterance(
        self,
        session_id: str,
        speaker: Speaker | None = None,
    ) -> Utterance | None:
        """
        最後の発話を取得.

        Args:
            session_id: セッションID
            speaker: 特定の話者でフィルタ（オプション）

        Returns:
            最後の発話、存在しない場合はNone
        """
        with self._lock:
            history = self._histories.get(session_id, [])
            if not history:
                return None

            if speaker is None:
                return history[-1]

            # 特定の話者の最後の発話を検索
            for utterance in reversed(history):
                if utterance.speaker == speaker:
                    return utterance

            return None

    def clear(self, session_id: str) -> None:
        """
        セッションの履歴をクリア.

        Args:
            session_id: セッションID
        """
        with self._lock:
            if session_id in self._histories:
                del self._histories[session_id]
                logger.info("Session %s: History cleared", session_id)

    def get_conversation_summary(
        self,
        session_id: str,
    ) -> str:
        """
        会話の要約を生成（LLMプロンプト用）.

        Args:
            session_id: セッションID

        Returns:
            会話履歴のテキスト要約
        """
        with self._lock:
            history = self._histories.get(session_id, [])
            if not history:
                return "=== 会話履歴 ===\n（履歴なし）"

            lines = ["=== 会話履歴 ==="]
            for utterance in history:
                speaker_label = (
                    "USER" if utterance.speaker == Speaker.USER else "PARTNER"
                )

                if (
                    utterance.emotion_context is not None
                    and utterance.speaker == Speaker.PARTNER
                ):
                    emotion = utterance.emotion_context.primary_emotion
                    lines.append(f"[{speaker_label}] ({emotion}) {utterance.text}")
                else:
                    lines.append(f"[{speaker_label}] {utterance.text}")

            return "\n".join(lines)
