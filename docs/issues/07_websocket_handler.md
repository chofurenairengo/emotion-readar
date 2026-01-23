# WebSocketハンドラの拡張

## 概要

既存のWebSocketハンドラを拡張し、感情スコア＋音声の解析リクエストを受け付け、LLM応答を返すメッセージタイプを追加する。

## 期待する仕様

### ファイル

```
server/app/api/routers/realtime.py  # 既存ファイルを修正
```

### 新規メッセージタイプ

#### クライアント → サーバー

| タイプ | 説明 |
|---|---|
| `PING` | 接続確認（既存） |
| `RESET` | セッションリセット（既存） |
| `ERROR_REPORT` | エラー報告（既存） |
| `ANALYSIS_REQUEST` | **新規**: 解析リクエスト |

#### サーバー → クライアント

| タイプ | 説明 |
|---|---|
| `PONG` | 接続確認応答（既存） |
| `RESET_ACK` | リセット確認（既存） |
| `ERROR_ACK` | エラー確認（既存） |
| `ERROR` | エラー通知（既存） |
| `ANALYSIS_RESPONSE` | **新規**: 解析結果 |

### ANALYSIS_REQUEST フォーマット

```json
{
    "type": "ANALYSIS_REQUEST",
    "session_id": "uuid-string",
    "timestamp": "2024-01-15T10:30:00Z",
    "emotion_scores": {
        "happy": 0.1,
        "sad": 0.05,
        "angry": 0.02,
        "surprised": 0.03,
        "confused": 0.7,
        "neutral": 0.1
    },
    "audio_data": "base64-encoded-audio-data",
    "audio_format": "wav"
}
```

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `type` | string | Yes | `"ANALYSIS_REQUEST"` 固定 |
| `session_id` | string | Yes | セッションID |
| `timestamp` | ISO8601 | Yes | クライアントタイムスタンプ |
| `emotion_scores` | object | Yes | 感情スコア辞書 |
| `audio_data` | string | No | Base64エンコード音声データ |
| `audio_format` | string | No | 音声フォーマット（wav/opus/pcm） |

### ANALYSIS_RESPONSE フォーマット

```json
{
    "type": "ANALYSIS_RESPONSE",
    "timestamp": "2024-01-15T10:30:01Z",
    "emotion": {
        "primary_emotion": "confused",
        "intensity": "high",
        "description": "相手は困惑しているようです",
        "suggestion": "説明を補足すると良いかもしれません"
    },
    "transcription": {
        "text": "えっと、どういう意味ですか？",
        "confidence": 0.92,
        "language": "ja",
        "duration_ms": 1500
    },
    "suggestions": [
        {
            "text": "申し訳ありません。もう少し詳しくご説明いたします。",
            "tone": "formal",
            "intent": "説明を補足する"
        },
        {
            "text": "ごめん、分かりにくかったね。つまりこういうことなんだ。",
            "tone": "casual",
            "intent": "カジュアルに補足する"
        },
        {
            "text": "分かりにくくてすみません。一緒に整理しましょうか？",
            "tone": "empathetic",
            "intent": "共感しながら補足を提案"
        }
    ],
    "situation_analysis": "相手は説明の内容を理解できておらず、困惑しています。具体例を交えて再度説明すると良いでしょう。",
    "processing_time_ms": 850
}
```

### 実装コード

```python
# app/api/routers/realtime.py

import base64
from datetime import datetime, timezone
import json
import logging

from fastapi import APIRouter, Depends, Query, WebSocket, WebSocketDisconnect

from app.api.dependencies import get_connection_manager, get_response_generator
from app.dto.audio import AudioFormat
from app.dto.processing import AnalysisRequest, AnalysisResponse
from app.services.connection_manager import ConnectionManager
from app.services.response_generator import ResponseGeneratorService

router = APIRouter()
logger = logging.getLogger(__name__)


@router.websocket("/realtime")
async def realtime(
    websocket: WebSocket,
    session_id: str = Query(...),
    connection_manager: ConnectionManager = Depends(get_connection_manager),
    response_generator: ResponseGeneratorService = Depends(get_response_generator),
) -> None:
    """リアルタイム通信WebSocketエンドポイント"""
    await connection_manager.connect(websocket, session_id)

    try:
        while True:
            raw_message = await websocket.receive_text()

            try:
                message = json.loads(raw_message)
            except json.JSONDecodeError:
                await _send_error(websocket, "Invalid JSON format")
                continue

            msg_type = message.get("type")

            if msg_type == "PING":
                await _handle_ping(websocket)

            elif msg_type == "RESET":
                await _handle_reset(websocket, session_id)

            elif msg_type == "ERROR_REPORT":
                await _handle_error_report(websocket, message)

            elif msg_type == "ANALYSIS_REQUEST":
                await _handle_analysis_request(
                    websocket,
                    message,
                    response_generator,
                )

            else:
                await _send_error(websocket, f"Unknown message type: {msg_type}")

    except WebSocketDisconnect:
        await connection_manager.disconnect(websocket)
    except Exception as e:
        logger.exception(f"WebSocket error: {e}")
        await connection_manager.disconnect(websocket)


async def _handle_analysis_request(
    websocket: WebSocket,
    message: dict,
    response_generator: ResponseGeneratorService,
) -> None:
    """ANALYSIS_REQUESTの処理"""
    try:
        # リクエストのバリデーション
        session_id = message.get("session_id")
        emotion_scores = message.get("emotion_scores")

        if not session_id or not emotion_scores:
            await _send_error(websocket, "Missing required fields: session_id, emotion_scores")
            return

        # 音声データのデコード（存在する場合）
        audio_data: bytes | None = None
        audio_format: AudioFormat | None = None

        if message.get("audio_data"):
            try:
                audio_data = base64.b64decode(message["audio_data"])
                audio_format = AudioFormat(message.get("audio_format", "wav"))
            except Exception as e:
                logger.warning(f"Failed to decode audio data: {e}")
                # 音声デコード失敗時は音声なしとして処理継続

        # 解析処理
        result = await response_generator.process(
            session_id=session_id,
            emotion_scores=emotion_scores,
            audio_data=audio_data,
            audio_format=audio_format,
        )

        # レスポンス送信
        await websocket.send_json(result.model_dump(mode="json"))

    except Exception as e:
        logger.exception(f"Analysis request failed: {e}")
        await _send_error(websocket, f"Analysis failed: {str(e)}")


async def _handle_ping(websocket: WebSocket) -> None:
    """PINGの処理"""
    await websocket.send_json({
        "type": "PONG",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    })


async def _handle_reset(websocket: WebSocket, session_id: str) -> None:
    """RESETの処理"""
    # TODO: セッションのリセット処理
    await websocket.send_json({
        "type": "RESET_ACK",
        "session_id": session_id,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    })


async def _handle_error_report(websocket: WebSocket, message: dict) -> None:
    """ERROR_REPORTの処理"""
    logger.error(f"Client error report: {message}")
    await websocket.send_json({
        "type": "ERROR_ACK",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    })


async def _send_error(websocket: WebSocket, error_message: str) -> None:
    """エラーメッセージ送信"""
    await websocket.send_json({
        "type": "ERROR",
        "message": error_message,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    })
```

### 音声データの制限

| 項目 | 制限値 |
|---|---|
| 最大音声サイズ | 5MB（Base64エンコード後） |
| 最大音声長 | 30秒 |
| 対応フォーマット | wav, opus, pcm |

### エラーレスポンス

```json
{
    "type": "ERROR",
    "message": "Analysis failed: STT service unavailable",
    "timestamp": "2024-01-15T10:30:01Z"
}
```

### 接続フロー

```
[Client]                                    [Server]
    │                                           │
    │ ──── WebSocket Connect ────────────────▶ │
    │      ?session_id=xxx                      │
    │                                           │
    │ ◀─── Connection Established ──────────── │
    │                                           │
    │ ──── PING ─────────────────────────────▶ │
    │ ◀─── PONG ─────────────────────────────  │
    │                                           │
    │ ──── ANALYSIS_REQUEST ─────────────────▶ │
    │      (emotion_scores + audio_data)        │
    │                                           │
    │      [STT処理]                            │
    │      [感情解釈]                           │
    │      [LLM推論]                            │
    │                                           │
    │ ◀─── ANALYSIS_RESPONSE ────────────────  │
    │      (emotion + suggestions)              │
    │                                           │
    │ ──── ANALYSIS_REQUEST ─────────────────▶ │
    │      (emotion_scores only, no audio)      │
    │                                           │
    │ ◀─── ANALYSIS_RESPONSE ────────────────  │
    │                                           │
```

### 依存性注入の更新

`app/api/dependencies.py` に追加:

```python
def get_response_generator() -> ResponseGeneratorService:
    """ResponseGeneratorServiceのDI"""
    # ... 実装
```

## 完了条件

- [ ] `ANALYSIS_REQUEST` メッセージを受信できる
- [ ] 音声データをBase64デコードできる
- [ ] `ResponseGeneratorService`を呼び出せる
- [ ] `ANALYSIS_RESPONSE` を正しく送信できる
- [ ] エラー時に適切なエラーレスポンスを返せる
- [ ] 既存のPING/RESET/ERROR_REPORTが動作する
- [ ] 単体テストが作成されている
- [ ] E2Eテストが作成されている
- [ ] `mypy`でエラーがない

## テストケース

```python
import pytest
import json
import base64
from fastapi.testclient import TestClient
from unittest.mock import AsyncMock, MagicMock, patch

from app.main import app


@pytest.fixture
def client():
    return TestClient(app)


def test_websocket_ping(client):
    """PING/PONGテスト"""
    with client.websocket_connect("/api/realtime?session_id=test") as ws:
        ws.send_json({"type": "PING"})
        response = ws.receive_json()
        assert response["type"] == "PONG"


def test_websocket_analysis_request(client):
    """ANALYSIS_REQUESTテスト"""
    with patch("app.api.dependencies.get_response_generator") as mock_gen:
        mock_service = MagicMock()
        mock_service.process = AsyncMock(return_value=MagicMock(
            model_dump=lambda mode: {
                "type": "ANALYSIS_RESPONSE",
                "emotion": {"primary_emotion": "neutral", ...},
                "suggestions": [...],
                ...
            }
        ))
        mock_gen.return_value = mock_service

        with client.websocket_connect("/api/realtime?session_id=test") as ws:
            ws.send_json({
                "type": "ANALYSIS_REQUEST",
                "session_id": "test",
                "timestamp": "2024-01-15T10:00:00Z",
                "emotion_scores": {"neutral": 0.8},
            })
            response = ws.receive_json()
            assert response["type"] == "ANALYSIS_RESPONSE"


def test_websocket_analysis_request_with_audio(client):
    """音声付きANALYSIS_REQUESTテスト"""
    fake_audio = base64.b64encode(b"fake-audio-data").decode()

    with patch("app.api.dependencies.get_response_generator") as mock_gen:
        # ... モック設定

        with client.websocket_connect("/api/realtime?session_id=test") as ws:
            ws.send_json({
                "type": "ANALYSIS_REQUEST",
                "session_id": "test",
                "timestamp": "2024-01-15T10:00:00Z",
                "emotion_scores": {"confused": 0.7},
                "audio_data": fake_audio,
                "audio_format": "wav",
            })
            response = ws.receive_json()
            assert response["type"] == "ANALYSIS_RESPONSE"


def test_websocket_invalid_message(client):
    """不正メッセージテスト"""
    with client.websocket_connect("/api/realtime?session_id=test") as ws:
        ws.send_text("not json")
        response = ws.receive_json()
        assert response["type"] == "ERROR"
```

## 関連Issue

- 親Issue: サーバーサイドMVP実装（Epic）
- 依存: #1 DTO定義, #6 応答生成サービス
