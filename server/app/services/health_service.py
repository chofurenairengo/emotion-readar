from __future__ import annotations

import asyncio
import logging

from langchain_core.messages import HumanMessage

from app.dto.response import HealthResponse
from app.infra.external.gemini_client import LLMClientFactory

logger = logging.getLogger(__name__)

_REACHABILITY_TIMEOUT_SECONDS = 5.0


async def _check_model_reachable() -> bool:
    """モデルに軽量リクエストを送り、疎通確認を行う."""
    try:
        client = LLMClientFactory.create_ft_client()
        response = await asyncio.wait_for(
            client.ainvoke([HumanMessage(content="ping")]),
            timeout=_REACHABILITY_TIMEOUT_SECONDS,
        )
        return response.content is not None
    except Exception:
        logger.warning("Model reachability check failed", exc_info=True)
        return False


async def get_health() -> HealthResponse:
    model_reachable = await _check_model_reachable()
    return HealthResponse(status="ok", model_reachable=model_reachable)
