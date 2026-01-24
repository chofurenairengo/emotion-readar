from __future__ import annotations

from typing import cast

from app.core.interfaces.agent import AgentInterface
from app.core.prompts.love_coach import get_agent_prompt
from app.dto.conversation import Utterance
from app.dto.emotion import EmotionInterpretation
from app.dto.llm import LLMResponseResult
from app.infra.external.gemini_client import LLMClientFactory


class ERAAgent(AgentInterface):
    def __init__(self) -> None:
        # 1. API接続の確立
        self.llm = LLMClientFactory.create_ft_client()

        # 2. 構造化出力の設定 (Schemaの適用)
        self.structured_llm = self.llm.with_structured_output(LLMResponseResult)

        # 3. 思考プロセスのロード (Promptの適用)
        self.prompt = get_agent_prompt()

        # 4. 思考回路(Chain)の結合
        self.chain = self.prompt | self.structured_llm

    def _format_input(
        self,
        conversation_context: list[Utterance],
        emotion_interpretation: EmotionInterpretation,
        partner_last_utterance: str,
    ) -> str:
        # 会話履歴をフォーマット
        history_text = ""
        for utterance in conversation_context[-5:]:  # 直近5発話
            speaker = "自分" if utterance.speaker.value == "user" else "相手"
            history_text += f"{speaker}: {utterance.text}\n"

        return (
            f"[感情: {emotion_interpretation.primary_emotion}"
            f"({emotion_interpretation.intensity})]"
            f"[状況: {emotion_interpretation.description}]"
            f"\n会話履歴:\n{history_text}"
            f"相手の発言: {partner_last_utterance}"
        )

    def _format_chat_history(
        self, conversation_context: list[Utterance]
    ) -> list[tuple[str, str]]:
        history: list[tuple[str, str]] = []
        for utterance in conversation_context[:-1]:  # 最後の発話を除く
            role = "human" if utterance.speaker.value == "user" else "ai"
            history.append((role, utterance.text))
        return history

    async def run(
        self,
        conversation_context: list[Utterance],
        emotion_interpretation: EmotionInterpretation,
        partner_last_utterance: str,
    ) -> LLMResponseResult:
        formatted_text = self._format_input(
            conversation_context, emotion_interpretation, partner_last_utterance
        )
        chat_history = self._format_chat_history(conversation_context)
        result = await self.chain.ainvoke(
            {"input_text": formatted_text, "chat_history": chat_history}
        )
        return cast(LLMResponseResult, result)


LoveCoachAgent = ERAAgent
