"""レート制限API統合テスト。"""

from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.services.rate_limiter import get_rate_limiter
from main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture(autouse=True)
def clear_rate_limiter() -> None:
    """各テスト前にレート制限をクリア"""
    get_rate_limiter().clear()


class TestRateLimitHeaders:
    """レート制限ヘッダーのテスト"""

    def test_rate_limit_headers_present(self, client: TestClient) -> None:
        """正常リクエストでX-RateLimit-*ヘッダーが付与される"""
        with patch("app.api.auth.verify_id_token") as mock_verify:
            mock_verify.return_value = {"uid": "test-user", "email": "test@example.com"}

            response = client.post(
                "/api/sessions",
                headers={"Authorization": "Bearer valid-token"},
            )

            assert "X-RateLimit-Limit" in response.headers
            assert "X-RateLimit-Remaining" in response.headers
            assert "X-RateLimit-Reset" in response.headers


class TestRateLimitEnforcement:
    """レート制限強制のテスト"""

    def test_returns_429_when_limit_exceeded(self, client: TestClient) -> None:
        """制限超過時に429が返される"""
        with patch("app.api.auth.verify_id_token") as mock_verify:
            mock_verify.return_value = {"uid": "test-user", "email": "test@example.com"}

            # セッション作成の制限(30)まで消費
            for _ in range(30):
                client.post(
                    "/api/sessions",
                    headers={"Authorization": "Bearer valid-token"},
                )

            # 31回目は429
            response = client.post(
                "/api/sessions",
                headers={"Authorization": "Bearer valid-token"},
            )

            assert response.status_code == 429
            assert response.json()["detail"] == "Rate limit exceeded. Try again later."
            assert "Retry-After" in response.headers
