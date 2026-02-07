import time
from unittest.mock import AsyncMock, Mock, patch

import pytest
from fastapi.testclient import TestClient

from app.services import health_service


def test_health_check_model_reachable(
    client: TestClient, mock_check_model_reachable: AsyncMock
) -> None:
    mock_check_model_reachable.return_value = True
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["model_reachable"] is True


def test_health_check_model_unreachable(
    client: TestClient, mock_check_model_reachable: AsyncMock
) -> None:
    mock_check_model_reachable.return_value = False
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["model_reachable"] is False


@pytest.mark.asyncio
async def test_check_model_reachable_cache() -> None:
    """キャッシュが有効な間は実際のAPI呼び出しが行われないことを確認."""
    # キャッシュをクリア
    health_service._model_reachable_cache = None

    with patch(
        "app.infra.external.gemini_client.LLMClientFactory.create_client"
    ) as mock_factory:
        mock_client = AsyncMock()
        mock_client.ainvoke = AsyncMock(return_value=AsyncMock(content="pong"))
        mock_factory.return_value = mock_client

        # 1回目：実際にAPI呼び出し
        result1 = await health_service._check_model_reachable()
        assert result1 is True
        assert mock_client.ainvoke.call_count == 1

        # 2回目：キャッシュから取得（API呼び出しなし）
        result2 = await health_service._check_model_reachable()
        assert result2 is True
        assert mock_client.ainvoke.call_count == 1  # 変化なし


@pytest.mark.asyncio
async def test_check_model_reachable_cache_expiry() -> None:
    """TTL切れ後は再度API呼び出しが行われることを確認."""
    # キャッシュをクリア
    health_service._model_reachable_cache = None

    call_counter = {"count": 0}

    async def mock_ainvoke(*args, **kwargs):
        call_counter["count"] += 1
        mock_response = AsyncMock()
        mock_response.content = "pong"
        return mock_response

    with patch(
        "app.infra.external.gemini_client.LLMClientFactory.create_client"
    ) as mock_factory:
        mock_client = AsyncMock()
        mock_client.ainvoke = mock_ainvoke
        mock_factory.return_value = mock_client

        # 1回目：API呼び出し（デフォルトのTTL=30秒）
        result1 = await health_service._check_model_reachable()
        assert result1 is True
        assert call_counter["count"] == 1

        # キャッシュが作成されていることを確認
        assert health_service._model_reachable_cache is not None

        # キャッシュのタイムスタンプを十分過去に設定（確実に期限切れ）
        health_service._model_reachable_cache.timestamp = time.time() - 100

        # 2回目：TTL切れのため再度API呼び出し
        result2 = await health_service._check_model_reachable()
        assert result2 is True
        assert call_counter["count"] == 2  # ainvokeが2回呼ばれる


@pytest.mark.asyncio
async def test_check_model_reachable_disabled() -> None:
    """HEALTH_CHECK_MODEL_ENABLED=False の場合、API呼び出しなしでTrueを返す."""
    # キャッシュをクリア
    health_service._model_reachable_cache = None

    with patch("app.services.health_service.get_settings") as mock_settings:
        mock_config = Mock()
        mock_config.HEALTH_CHECK_MODEL_ENABLED = False
        mock_settings.return_value = mock_config

        # モデルチェック無効化時は常にTrue（API呼び出しなし）
        result = await health_service._check_model_reachable()
        assert result is True


@pytest.mark.asyncio
async def test_check_model_reachable_failure_cached() -> None:
    """失敗結果もキャッシュされることを確認."""
    # キャッシュをクリア
    health_service._model_reachable_cache = None

    with patch(
        "app.infra.external.gemini_client.LLMClientFactory.create_client"
    ) as mock_factory:
        # 例外を発生させる
        mock_factory.side_effect = Exception("Connection failed")

        # 1回目：失敗
        result1 = await health_service._check_model_reachable()
        assert result1 is False
        assert mock_factory.call_count == 1

        # 2回目：キャッシュされた失敗結果を返す（再試行しない）
        result2 = await health_service._check_model_reachable()
        assert result2 is False
        assert mock_factory.call_count == 1  # 変化なし
