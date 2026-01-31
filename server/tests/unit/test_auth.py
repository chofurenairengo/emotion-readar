"""JWT検証 Dependency のユニットテスト。"""

from __future__ import annotations

from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.api.auth import verify_websocket_token


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


def test_access_other_user_session_returns_403(client: TestClient) -> None:
    """他のユーザーのセッションにアクセスすると403が返る。"""
    # ユーザーAでセッション作成
    user_a_claims = {"uid": "user-a-123", "email": "a@example.com"}
    with patch(
        "app.api.auth.verify_id_token",
        return_value=user_a_claims,
    ):
        create_resp = client.post(
            "/api/sessions",
            headers={"Authorization": "Bearer user-a-token"},
        )
        session_id = create_resp.json()["id"]

    # ユーザーBでそのセッションにアクセス
    user_b_claims = {"uid": "user-b-456", "email": "b@example.com"}
    with patch(
        "app.api.auth.verify_id_token",
        return_value=user_b_claims,
    ):
        get_resp = client.get(
            f"/api/sessions/{session_id}",
            headers={"Authorization": "Bearer user-b-token"},
        )
    assert get_resp.status_code == 403
    assert "permission" in get_resp.json()["detail"].lower()


def test_end_other_user_session_returns_403(client: TestClient) -> None:
    """他のユーザーのセッションを終了しようとすると403が返る。"""
    # ユーザーAでセッション作成
    user_a_claims = {"uid": "user-a-123", "email": "a@example.com"}
    with patch(
        "app.api.auth.verify_id_token",
        return_value=user_a_claims,
    ):
        create_resp = client.post(
            "/api/sessions",
            headers={"Authorization": "Bearer user-a-token"},
        )
        session_id = create_resp.json()["id"]

    # ユーザーBでそのセッションを終了しようとする
    user_b_claims = {"uid": "user-b-456", "email": "b@example.com"}
    with patch(
        "app.api.auth.verify_id_token",
        return_value=user_b_claims,
    ):
        end_resp = client.post(
            f"/api/sessions/{session_id}/end",
            headers={"Authorization": "Bearer user-b-token"},
        )
    assert end_resp.status_code == 403
    assert "permission" in end_resp.json()["detail"].lower()


# verify_websocket_token のユニットテスト


def test_verify_websocket_token_empty_string() -> None:
    """空文字列でValueErrorが発生する."""
    with pytest.raises(ValueError, match="Missing"):
        verify_websocket_token("")


def test_verify_websocket_token_invalid() -> None:
    """無効なトークンでValueErrorが発生する."""
    with patch("app.api.auth.verify_id_token", side_effect=Exception("Invalid")):
        with pytest.raises(ValueError, match="Invalid or expired"):
            verify_websocket_token("bad-token")


def test_verify_websocket_token_valid() -> None:
    """有効なトークンでユーザー情報が返る."""
    mock_claims = {"uid": "ws-user-123", "email": "ws@example.com"}
    with patch("app.api.auth.verify_id_token", return_value=mock_claims):
        result = verify_websocket_token("valid-token")
    assert result["uid"] == "ws-user-123"
    assert result["email"] == "ws@example.com"
