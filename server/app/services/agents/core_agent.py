from langchain_google_genai import ChatGoogleGenerativeAI
from server.core.config import settings
from server.services.agents.schemas import AgentInput, CoachingResponse
from server.services.agents.prompts import get_chat_prompt

class CommAgent:
    def __init__(self):
        self.llm = ChatGoogleGenerativeAI(
            model="gemini-1.5-flash",
            google_api_key=settings.GEMINI_API_KEY,
            temperature=0.5, # 評価型プロンプトなので少し創造性を抑える
        )
        self.structured_llm = self.llm.with_structured_output(CoachingResponse)
        self.prompt = get_chat_prompt()
        self.chain = self.prompt | self.structured_llm

    def _format_input_string(self, input_data: AgentInput) -> str:
        """
        プロンプトのFew-Shot例と全く同じフォーマットの文字列を作成する。
        ここがズレるとAIの精度が下がる。
        """
        nv = input_data.non_verbal
        
        # 1. 非言語情報の言語化 (簡易ロジック)
        smile_str = "笑顔" if nv.smile_score > 0.7 else "真顔"
        
        # 2. タグ形式への整形
        # プロンプト例: [場所: カフェ][関係: デート][非言語: ...][履歴: ...]
        formatted_text = (
            f"[場所: {nv.place}]"
            f"[関係: {nv.relation}]"
            f"[非言語: 表情({smile_str}), 視線({nv.gaze_direction}), 声({nv.voice_tone})]"
            f"\n相手の発言: {input_data.text}"
        )
        return formatted_text

    async def run(self, input_data: AgentInput) -> CoachingResponse:
        # プロンプトが期待する形式にデータを整形
        formatted_input = self._format_input_string(input_data)
        
        # Chain実行
        # input_text に整形済みの文字列を渡すのがポイント
        response = await self.chain.ainvoke({
            "input_text": formatted_input,
            "chat_history": input_data.history
        })
        
        return response
    