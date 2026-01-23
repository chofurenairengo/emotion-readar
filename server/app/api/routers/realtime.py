from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Depends, Query, WebSocket, WebSocketDisconnect

from app.api.dependencies import get_connection_manager
from app.services.connection_manager import ConnectionManager

logger = logging.getLogger(__name__)

router = APIRouter(tags=["realtime"])

_ALLOWED_TYPES = {"PING", "RESET", "ERROR_REPORT"}


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
    connection_manager: ConnectionManager = Depends(get_connection_manager),
) -> None:
    await connection_manager.connect(websocket, session_id)
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
    except WebSocketDisconnect:
        await connection_manager.disconnect(websocket)
