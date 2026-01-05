from __future__ import annotations

import asyncio
import logging
from typing import Any

from fastapi import WebSocket

logger = logging.getLogger(__name__)


class ConnectionManager:
    def __init__(self) -> None:
        self._session_connections: dict[str, set[WebSocket]] = {}
        self._connection_sessions: dict[WebSocket, str] = {}
        self._lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket, session_id: str) -> None:
        await websocket.accept()
        async with self._lock:
            self._connection_sessions[websocket] = session_id
            self._session_connections.setdefault(session_id, set()).add(websocket)

    async def disconnect(self, websocket: WebSocket) -> None:
        async with self._lock:
            session_id = self._connection_sessions.pop(websocket, None)
            if session_id is None:
                return
            connections = self._session_connections.get(session_id)
            if connections is None:
                return
            connections.discard(websocket)
            if not connections:
                self._session_connections.pop(session_id, None)

    async def send_to_session(self, session_id: str, payload: dict[str, Any]) -> None:
        async with self._lock:
            connections = list(self._session_connections.get(session_id, set()))
        await self._send_to_connections(connections, payload)

    async def broadcast(self, payload: dict[str, Any]) -> None:
        async with self._lock:
            connections = list(self._connection_sessions.keys())
        await self._send_to_connections(connections, payload)

    async def _send_to_connections(
        self,
        connections: list[WebSocket],
        payload: dict[str, Any],
    ) -> None:
        for websocket in connections:
            try:
                await websocket.send_json(payload)
            except Exception:
                logger.exception("Failed to send WebSocket payload")
                await self.disconnect(websocket)
