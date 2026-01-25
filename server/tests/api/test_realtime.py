"""WebSocket realtime endpoint tests."""

import base64
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def mock_auth_and_session():
    """認証とセッション検証のモック."""
    mock_claims = {"uid": "test-user-123", "email": "test@example.com"}
    mock_session = MagicMock()
    mock_session.owner_id = "test-user-123"

    with patch(
        "app.api.routers.realtime.verify_websocket_token",
        return_value=mock_claims,
    ), patch(
        "app.api.routers.realtime.get_session_service",
    ) as mock_get_service:
        mock_service = MagicMock()
        mock_service.get_session.return_value = mock_session
        mock_get_service.return_value = mock_service
        yield


class TestWebSocketAuthentication:
    """WebSocket認証テスト."""

    def test_invalid_token_closes_connection(self, client: TestClient) -> None:
        """無効なトークンで接続がクローズされる."""
        with patch(
            "app.api.routers.realtime.verify_websocket_token",
            side_effect=ValueError("Invalid or expired token"),
        ):
            with client.websocket_connect("/api/realtime?session_id=test&token=invalid") as ws:
                # 接続は確立されるが、すぐにクローズされる
                with pytest.raises(WebSocketDisconnect) as exc_info:
                    ws.receive_json()
                assert exc_info.value.code == 4001

    def test_session_not_found_closes_connection(self, client: TestClient) -> None:
        """セッションが見つからない場合は接続がクローズされる."""
        mock_claims = {"uid": "test-user-123", "email": "test@example.com"}
        with patch(
            "app.api.routers.realtime.verify_websocket_token",
            return_value=mock_claims,
        ), patch(
            "app.api.routers.realtime.get_session_service",
        ) as mock_get_service:
            mock_service = MagicMock()
            mock_service.get_session.return_value = None
            mock_get_service.return_value = mock_service
            with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
                with pytest.raises(WebSocketDisconnect) as exc_info:
                    ws.receive_json()
                assert exc_info.value.code == 4004

    def test_wrong_owner_closes_connection(self, client: TestClient) -> None:
        """他人のセッションへの接続がクローズされる."""
        mock_claims = {"uid": "test-user-123", "email": "test@example.com"}
        mock_session = MagicMock()
        mock_session.owner_id = "different-user-456"

        with patch(
            "app.api.routers.realtime.verify_websocket_token",
            return_value=mock_claims,
        ), patch(
            "app.api.routers.realtime.get_session_service",
        ) as mock_get_service:
            mock_service = MagicMock()
            mock_service.get_session.return_value = mock_session
            mock_get_service.return_value = mock_service
            with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
                with pytest.raises(WebSocketDisconnect) as exc_info:
                    ws.receive_json()
                assert exc_info.value.code == 4003


class TestWebSocketPing:
    """PING/PONGテスト."""

    def test_ping_returns_pong(self, client: TestClient, mock_auth_and_session) -> None:
        """PINGメッセージにPONGで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
            ws.send_json({"type": "PING"})
            response = ws.receive_json()
            assert response["type"] == "PONG"
            assert "timestamp" in response


class TestWebSocketReset:
    """RESETテスト."""

    def test_reset_returns_ack(self, client: TestClient, mock_auth_and_session) -> None:
        """RESETメッセージにRESET_ACKで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
            ws.send_json({"type": "RESET"})
            response = ws.receive_json()
            assert response["type"] == "RESET_ACK"
            assert "timestamp" in response


class TestWebSocketErrorReport:
    """ERROR_REPORTテスト."""

    def test_error_report_returns_ack(self, client: TestClient, mock_auth_and_session) -> None:
        """ERROR_REPORTメッセージにERROR_ACKで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
            ws.send_json({"type": "ERROR_REPORT", "error": "test error"})
            response = ws.receive_json()
            assert response["type"] == "ERROR_ACK"
            assert "timestamp" in response


class TestWebSocketInvalidMessage:
    """不正メッセージテスト."""

    def test_invalid_json_returns_error(self, client: TestClient, mock_auth_and_session) -> None:
        """不正なJSONにERRORで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
            ws.send_text("not json")
            response = ws.receive_json()
            assert response["type"] == "ERROR"
            assert "Invalid JSON" in response["message"]

    def test_unknown_type_returns_error(self, client: TestClient, mock_auth_and_session) -> None:
        """不明なメッセージタイプにERRORで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
            ws.send_json({"type": "UNKNOWN_TYPE"})
            response = ws.receive_json()
            assert response["type"] == "ERROR"
            assert "Unsupported message type" in response["message"]


class TestWebSocketAnalysisRequest:
    """ANALYSIS_REQUESTテスト."""

    @pytest.fixture
    def mock_response_generator(self) -> MagicMock:
        """ResponseGeneratorServiceのモック."""
        mock_service = MagicMock()
        mock_response = MagicMock()
        mock_response.model_dump.return_value = {
            "type": "ANALYSIS_RESPONSE",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "emotion": {
                "primary_emotion": "neutral",
                "intensity": "medium",
                "description": "相手は平静です",
            },
            "transcription": None,
            "suggestions": [
                {"text": "応答1", "intent": "テスト1"},
                {"text": "応答2", "intent": "テスト2"},
            ],
            "situation_analysis": "テスト分析",
            "processing_time_ms": 100,
        }
        mock_service.process = AsyncMock(return_value=mock_response)
        return mock_service

    def test_analysis_request_returns_response(
        self, client: TestClient, mock_response_generator: MagicMock, mock_auth_and_session
    ) -> None:
        """ANALYSIS_REQUESTにANALYSIS_RESPONSEで応答する."""
        with patch(
            "app.api.routers.realtime.get_response_generator",
            return_value=mock_response_generator,
        ):
            with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
                ws.send_json(
                    {
                        "type": "ANALYSIS_REQUEST",
                        "session_id": "test",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "emotion_scores": {"neutral": 0.8, "happy": 0.2},
                    }
                )
                response = ws.receive_json()
                assert response["type"] == "ANALYSIS_RESPONSE"

    def test_analysis_request_with_audio(
        self, client: TestClient, mock_response_generator: MagicMock, mock_auth_and_session
    ) -> None:
        """音声付きANALYSIS_REQUESTが正しく処理される."""
        fake_audio = base64.b64encode(b"fake-audio-data").decode()

        with patch(
            "app.api.routers.realtime.get_response_generator",
            return_value=mock_response_generator,
        ):
            with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
                ws.send_json(
                    {
                        "type": "ANALYSIS_REQUEST",
                        "session_id": "test",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "emotion_scores": {"confused": 0.7},
                        "audio_data": fake_audio,
                        "audio_format": "wav",
                    }
                )
                response = ws.receive_json()
                assert response["type"] == "ANALYSIS_RESPONSE"

    def test_analysis_request_missing_session_id_returns_error(
        self, client: TestClient, mock_auth_and_session
    ) -> None:
        """session_idがない場合はエラーを返す."""
        with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
            ws.send_json(
                {
                    "type": "ANALYSIS_REQUEST",
                    "timestamp": "2024-01-15T10:00:00Z",
                    "emotion_scores": {"neutral": 0.8},
                }
            )
            response = ws.receive_json()
            assert response["type"] == "ERROR"
            assert "session_id" in response["message"]

    def test_analysis_request_missing_emotion_scores_returns_error(
        self, client: TestClient, mock_auth_and_session
    ) -> None:
        """emotion_scoresがない場合はエラーを返す."""
        with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
            ws.send_json(
                {
                    "type": "ANALYSIS_REQUEST",
                    "session_id": "test",
                    "timestamp": "2024-01-15T10:00:00Z",
                }
            )
            response = ws.receive_json()
            assert response["type"] == "ERROR"
            assert "emotion_scores" in response["message"]

    def test_analysis_request_invalid_audio_continues_processing(
        self, client: TestClient, mock_response_generator: MagicMock, mock_auth_and_session
    ) -> None:
        """無効なaudio_dataでも処理を継続する."""
        with patch(
            "app.api.routers.realtime.get_response_generator",
            return_value=mock_response_generator,
        ):
            with client.websocket_connect("/api/realtime?session_id=test&token=valid") as ws:
                ws.send_json(
                    {
                        "type": "ANALYSIS_REQUEST",
                        "session_id": "test",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "emotion_scores": {"neutral": 0.8},
                        "audio_data": "not-valid-base64!!!",
                        "audio_format": "wav",
                    }
                )
                response = ws.receive_json()
                assert response["type"] == "ANALYSIS_RESPONSE"
