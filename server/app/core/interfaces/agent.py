from __future__ import annotations

from abc import ABC, abstractmethod

from app.dto.request import AgentInput
from app.dto.response import CoachingResponse


class AgentInterface(ABC):
    """全てのエージェントが守るべき契約"""

    @abstractmethod
    async def run(self, input_data: AgentInput) -> CoachingResponse:
        """入力を受け取り、構造化されたコーチング結果を返す"""
        raise NotImplementedError
