from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def mock_check_model_reachable():
    """_check_model_reachable をモック化するフィクスチャ(準備関数).
    
    このフィクスチャを使わないテストでは実際の関数が実行される。
    """
    with patch(
        "app.services.health_service._check_model_reachable",
        new_callable=AsyncMock,
        return_value=True,
    ) as mock:
        yield mock
