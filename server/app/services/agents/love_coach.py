from __future__ import annotations

from app.core.interfaces.agent import AgentInterface
from app.core.prompts.love_coach import get_agent_prompt
from app.dto.request import AgentInput
from app.dto.response import CoachingResponse
from app.infra.external.gemini_client import LLMClientFactory


class CommXRAgent(AgentInterface):
	def __init__(self) -> None:
		# 1. API接続の確立
		self.llm = LLMClientFactory.create_ft_client()

		# 2. 構造化出力の設定 (Schemaの適用)
		self.structured_llm = self.llm.with_structured_output(CoachingResponse)

		# 3. 思考プロセスのロード (Promptの適用)
		self.prompt = get_agent_prompt()

		# 4. 思考回路(Chain)の結合
		self.chain = self.prompt | self.structured_llm

	def _format_input(self, data: AgentInput) -> str:
		nv = data.non_verbal
		return (
			f"[場所: {nv.place}][関係: {nv.relation}]"
			f"[非言語: 表情({nv.smile_score}), 視線({nv.gaze_direction})]"
			f"\n相手の発言: {data.text}"
		)

	async def run(self, input_data: AgentInput) -> CoachingResponse:
		formatted_text = self._format_input(input_data)
		return await self.chain.ainvoke(
			{"input_text": formatted_text, "chat_history": input_data.history}
		)


LoveCoachAgent = CommXRAgent
