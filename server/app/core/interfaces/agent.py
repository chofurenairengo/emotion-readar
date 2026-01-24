from __future__ import annotations

from abc import ABC, abstractmethod

from app.dto.conversation import Utterance
from app.dto.emotion import EmotionInterpretation
from app.dto.llm import LLMResponseResult


class AgentInterface(ABC):
    """全てのエージェントが守るべき契約."""

    @abstractmethod
    async def run(
        self,
        conversation_context: list[Utterance],
        emotion_interpretation: EmotionInterpretation,
        partner_last_utterance: str,
    ) -> LLMResponseResult:
        """入力を受け取り、構造化されたコーチング結果を返す."""
        raise NotImplementedError
