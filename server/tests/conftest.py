from collections.abc import Iterator
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from main import app


@pytest.fixture
def client() -> TestClient:
    with (
        patch(
            "app.core.lifespan.initialize_credentials",
            return_value=(MagicMock(), "test-project"),
        ),
        patch("app.core.lifespan.initialize_firebase"),
        patch("app.core.lifespan.get_stt_service"),
        patch("app.core.lifespan.get_llm_service"),
    ):
        return TestClient(app)


@pytest.fixture
def mock_check_model_reachable() -> Iterator[AsyncMock]:
    """_check_model_reachable をモック化するフィクスチャ(準備関数).

    このフィクスチャを使わないテストでは実際の関数が実行される。
    """
    with patch(
        "app.services.health_service._check_model_reachable",
        new_callable=AsyncMock,
        return_value=True,
    ) as mock:
        yield mock
