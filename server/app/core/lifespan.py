"""アプリケーションのライフスパンイベント.

起動時にADC認証を一括実行し、各GCPサービスを事前初期化する。
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.dependencies import get_llm_service, get_stt_service
from app.infra.firebase import initialize_firebase
from app.infra.gcp_auth import initialize_credentials

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """アプリケーション起動時にADC認証を一括実行し、サービスを事前初期化する."""
    logger.info("ADC認証を開始します")
    initialize_credentials()

    # サービスを事前初期化
    initialize_firebase()
    get_stt_service()
    get_llm_service()

    logger.info("全サービスの事前初期化完了")
    yield
