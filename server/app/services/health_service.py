from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass

from langchain_core.messages import HumanMessage

from app.core.config import get_settings
from app.dto.response import HealthResponse
from app.infra.external.gemini_client import LLMClientFactory

logger = logging.getLogger(__name__)

_REACHABILITY_TIMEOUT_SECONDS = 5.0


@dataclass
class _CacheEntry:
    """モデル疎通確認結果のキャッシュエントリ."""

    result: bool
    timestamp: float


# モジュールレベルのキャッシュ（シンプルな実装）
_model_reachable_cache: _CacheEntry | None = None


async def _check_model_reachable() -> bool:
    """モデルに軽量リクエストを送り、疎通確認を行う.
    
    キャッシュが有効でTTL内であれば、キャッシュされた結果を返す。
    """
    global _model_reachable_cache

    settings = get_settings()

    # 設定で無効化されている場合は常にTrueを返す
    if not settings.HEALTH_CHECK_MODEL_ENABLED:
        return True

    # キャッシュが有効かチェック
    now = time.time()
    if _model_reachable_cache is not None:
        age = now - _model_reachable_cache.timestamp
        if age < settings.HEALTH_CHECK_MODEL_CACHE_TTL:
            logger.debug(
                "Using cached model reachability result (age: %.1fs)", age
            )
            return _model_reachable_cache.result

    # キャッシュがないか期限切れの場合、実際にチェック
    try:
        client = LLMClientFactory.create_ft_client()
        response = await asyncio.wait_for(
            client.ainvoke([HumanMessage(content="ping")]),
            timeout=_REACHABILITY_TIMEOUT_SECONDS,
        )
        result = response.content is not None
        _model_reachable_cache = _CacheEntry(result=result, timestamp=now)
        logger.info("Model reachability check completed: %s", result)
        return result
    except Exception:
        logger.warning("Model reachability check failed", exc_info=True)
        result = False
        _model_reachable_cache = _CacheEntry(result=result, timestamp=now)
        return result


async def get_health() -> HealthResponse:
    model_reachable = await _check_model_reachable()
    return HealthResponse(status="ok", model_reachable=model_reachable)
