from __future__ import annotations

import base64
import logging
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Depends, Query, WebSocket, WebSocketDisconnect

from app.api.auth import verify_websocket_token
from app.api.dependencies import (
    get_connection_manager,
    get_response_generator,
    get_session_service,
)
from app.dto.audio import AudioFormat
from app.services.connection_manager import ConnectionManager

logger = logging.getLogger(__name__)

router = APIRouter(tags=["realtime"])

_ALLOWED_TYPES = {"PING", "RESET", "ERROR_REPORT", "ANALYSIS_REQUEST"}


def _utc_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _error_payload(message: str, detail: Any | None = None) -> dict[str, Any]:
    payload: dict[str, Any] = {"type": "ERROR", "message": message}
    if detail is not None:
        payload["detail"] = detail
    payload["timestamp"] = _utc_iso()
    return payload


@router.websocket("/realtime")
async def realtime(
    websocket: WebSocket,
    session_id: str = Query(...),
    token: str = Query(...),
    connection_manager: ConnectionManager = Depends(get_connection_manager),
) -> None:
    # 先に accept() を実行
    await websocket.accept()

    # トークン検証
    try:
        user_info = verify_websocket_token(token)
    except ValueError as exc:
        await websocket.close(code=4001, reason=str(exc))
        return

    # セッション所有者検証
    session_service = get_session_service()
    session = session_service.get_session(session_id)
    if session is None:
        await websocket.close(code=4004, reason="Session not found")
        return
    if session.owner_id != user_info["uid"]:
        await websocket.close(code=4003, reason="You don't have permission to access this session")
        return

    await connection_manager.register(websocket, session_id)
    try:
        while True:
            try:
                data = await websocket.receive_json()
            except ValueError:
                await websocket.send_json(_error_payload("Invalid JSON"))
                continue

            message_type = None
            if isinstance(data, dict):
                message_type = data.get("type")

            if message_type not in _ALLOWED_TYPES:
                await websocket.send_json(
                    _error_payload("Unsupported message type", message_type)
                )
                continue

            if message_type == "PING":
                await websocket.send_json({"type": "PONG", "timestamp": _utc_iso()})
                continue

            if message_type == "RESET":
                await websocket.send_json(
                    {"type": "RESET_ACK", "timestamp": _utc_iso()}
                )
                continue

            if message_type == "ERROR_REPORT":
                logger.warning("Client error report", extra={"payload": data})
                await websocket.send_json(
                    {"type": "ERROR_ACK", "timestamp": _utc_iso()}
                )
                continue

            if message_type == "ANALYSIS_REQUEST":
                await _handle_analysis_request(websocket, data)
    except WebSocketDisconnect:
        await connection_manager.disconnect(websocket)


async def _handle_analysis_request(
    websocket: WebSocket,
    message: dict[str, Any],
) -> None:
    """ANALYSIS_REQUESTの処理.

    Args:
        websocket: WebSocket接続
        message: 受信したメッセージ
    """
    # 必須フィールドのバリデーション
    session_id = message.get("session_id")
    emotion_scores = message.get("emotion_scores")

    if not session_id or not emotion_scores:
        missing_fields = []
        if not session_id:
            missing_fields.append("session_id")
        if not emotion_scores:
            missing_fields.append("emotion_scores")
        await websocket.send_json(
            _error_payload(f"Missing required fields: {', '.join(missing_fields)}")
        )
        return

    # 音声データのデコード（存在する場合）
    audio_data: bytes | None = None
    audio_format: AudioFormat | None = None

    if message.get("audio_data"):
        try:
            audio_data = base64.b64decode(message["audio_data"])
            format_str = message.get("audio_format", "wav")
            audio_format = AudioFormat(format_str)
        except Exception as e:
            logger.warning("Failed to decode audio data: %s", e)
            # 音声デコード失敗時は処理を継続（音声なしとして扱う）
            audio_data = None
            audio_format = None

    try:
        # ResponseGeneratorServiceを取得して処理
        response_generator = get_response_generator()
        result = await response_generator.process(
            session_id=session_id,
            emotion_scores=emotion_scores,
            audio_data=audio_data,
            audio_format=audio_format,
        )

        # レスポンス送信
        await websocket.send_json(result.model_dump(mode="json"))

    except NotImplementedError as e:
        logger.error("ResponseGeneratorService not implemented: %s", e)
        await websocket.send_json(_error_payload("Service not available", str(e)))
    except Exception as e:
        logger.exception("Analysis request failed: %s", e)
        await websocket.send_json(_error_payload(f"Analysis failed: {str(e)}"))
