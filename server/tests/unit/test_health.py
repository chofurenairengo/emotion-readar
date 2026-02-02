from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient


def test_health_check_model_reachable(client: TestClient) -> None:
    with patch(
        "app.services.health_service._check_model_reachable",
        new_callable=AsyncMock,
        return_value=True,
    ):
        response = client.get("/api/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "ok"
        assert data["model_reachable"] is True


def test_health_check_model_unreachable(client: TestClient) -> None:
    with patch(
        "app.services.health_service._check_model_reachable",
        new_callable=AsyncMock,
        return_value=False,
    ):
        response = client.get("/api/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "ok"
        assert data["model_reachable"] is False
