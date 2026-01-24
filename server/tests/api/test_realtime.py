"""WebSocket realtime endpoint tests."""

import base64
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


class TestWebSocketPing:
    """PING/PONGテスト."""

    def test_ping_returns_pong(self, client: TestClient) -> None:
        """PINGメッセージにPONGで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test") as ws:
            ws.send_json({"type": "PING"})
            response = ws.receive_json()
            assert response["type"] == "PONG"
            assert "timestamp" in response


class TestWebSocketReset:
    """RESETテスト."""

    def test_reset_returns_ack(self, client: TestClient) -> None:
        """RESETメッセージにRESET_ACKで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test") as ws:
            ws.send_json({"type": "RESET"})
            response = ws.receive_json()
            assert response["type"] == "RESET_ACK"
            assert "timestamp" in response


class TestWebSocketErrorReport:
    """ERROR_REPORTテスト."""

    def test_error_report_returns_ack(self, client: TestClient) -> None:
        """ERROR_REPORTメッセージにERROR_ACKで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test") as ws:
            ws.send_json({"type": "ERROR_REPORT", "error": "test error"})
            response = ws.receive_json()
            assert response["type"] == "ERROR_ACK"
            assert "timestamp" in response


class TestWebSocketInvalidMessage:
    """不正メッセージテスト."""

    def test_invalid_json_returns_error(self, client: TestClient) -> None:
        """不正なJSONにERRORで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test") as ws:
            ws.send_text("not json")
            response = ws.receive_json()
            assert response["type"] == "ERROR"
            assert "Invalid JSON" in response["message"]

    def test_unknown_type_returns_error(self, client: TestClient) -> None:
        """不明なメッセージタイプにERRORで応答する."""
        with client.websocket_connect("/api/realtime?session_id=test") as ws:
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
        self, client: TestClient, mock_response_generator: MagicMock
    ) -> None:
        """ANALYSIS_REQUESTにANALYSIS_RESPONSEで応答する."""
        with patch(
            "app.api.routers.realtime.get_response_generator",
            return_value=mock_response_generator,
        ):
            with client.websocket_connect("/api/realtime?session_id=test") as ws:
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
        self, client: TestClient, mock_response_generator: MagicMock
    ) -> None:
        """音声付きANALYSIS_REQUESTが正しく処理される."""
        fake_audio = base64.b64encode(b"fake-audio-data").decode()

        with patch(
            "app.api.routers.realtime.get_response_generator",
            return_value=mock_response_generator,
        ):
            with client.websocket_connect("/api/realtime?session_id=test") as ws:
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
        self, client: TestClient
    ) -> None:
        """session_idがない場合はエラーを返す."""
        with client.websocket_connect("/api/realtime?session_id=test") as ws:
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
        self, client: TestClient
    ) -> None:
        """emotion_scoresがない場合はエラーを返す."""
        with client.websocket_connect("/api/realtime?session_id=test") as ws:
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
        self, client: TestClient, mock_response_generator: MagicMock
    ) -> None:
        """無効なaudio_dataでも処理を継続する."""
        with patch(
            "app.api.routers.realtime.get_response_generator",
            return_value=mock_response_generator,
        ):
            with client.websocket_connect("/api/realtime?session_id=test") as ws:
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
