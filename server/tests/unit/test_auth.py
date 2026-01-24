"""JWT検証 Dependency のユニットテスト。"""

from __future__ import annotations

from unittest.mock import patch

from fastapi.testclient import TestClient


def test_health_endpoint_no_auth_required(client: TestClient) -> None:
    """health エンドポイントは認証不要。"""
    response = client.get("/api/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_missing_token_returns_401(client: TestClient) -> None:
    """Authorization ヘッダーなしで 401 が返る。"""
    response = client.post("/api/sessions")
    assert response.status_code == 401
    assert response.json()["detail"] == "Missing authentication token"


def test_invalid_bearer_format_returns_401(client: TestClient) -> None:
    """Bearer 形式でないヘッダーで 401 が返る。"""
    response = client.post(
        "/api/sessions",
        headers={"Authorization": "Basic abc123"},
    )
    assert response.status_code == 401


def test_invalid_token_returns_401(client: TestClient) -> None:
    """無効なトークンで 401 が返る。"""
    with patch(
        "app.api.auth.verify_id_token",
        side_effect=Exception("Invalid token"),
    ):
        response = client.post(
            "/api/sessions",
            headers={"Authorization": "Bearer invalid-token"},
        )
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid or expired token"


def test_valid_token_returns_success(client: TestClient) -> None:
    """有効なトークンでセッション作成が成功する。"""
    mock_claims = {"uid": "test-user-123", "email": "test@example.com"}
    with patch(
        "app.api.auth.verify_id_token",
        return_value=mock_claims,
    ):
        response = client.post(
            "/api/sessions",
            headers={"Authorization": "Bearer valid-token"},
        )
    assert response.status_code == 201
    data = response.json()
    assert "id" in data
    assert data["status"] == "active"


def test_valid_token_on_get_session(client: TestClient) -> None:
    """認証付きでセッション取得ができる。"""
    mock_claims = {"uid": "test-user-123", "email": "test@example.com"}
    with patch(
        "app.api.auth.verify_id_token",
        return_value=mock_claims,
    ):
        # まずセッション作成
        create_resp = client.post(
            "/api/sessions",
            headers={"Authorization": "Bearer valid-token"},
        )
        session_id = create_resp.json()["id"]

        # セッション取得
        get_resp = client.get(
            f"/api/sessions/{session_id}",
            headers={"Authorization": "Bearer valid-token"},
        )
    assert get_resp.status_code == 200
    assert get_resp.json()["id"] == session_id


def test_features_endpoint_requires_auth(client: TestClient) -> None:
    """features エンドポイントも認証必須。"""
    response = client.post("/api/features", json={"session_id": "s", "features": {}})
    assert response.status_code == 401


def test_www_authenticate_header_present(client: TestClient) -> None:
    """401 レスポンスに WWW-Authenticate ヘッダーが含まれる。"""
    response = client.post("/api/sessions")
    assert response.status_code == 401
    assert response.headers.get("WWW-Authenticate") == "Bearer"
