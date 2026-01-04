# インターフェイス定義
from abc import ABC, abstractmethod

from app.services.agents.schemas import AgentInput, CoachingResponse


class AgentInterface(ABC):
    """全てのエージェントが守るべき契約"""

    @abstractmethod
    async def run(self, input_data: AgentInput) -> CoachingResponse:
        """入力を受け取り、構造化されたコーチング結果を返す"""
        pass
