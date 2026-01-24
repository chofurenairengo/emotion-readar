from fastapi import APIRouter, WebSocket, status

router = APIRouter(tags=["chat"])


@router.post("/chat", status_code=status.HTTP_501_NOT_IMPLEMENTED)
def chat_stub() -> dict[str, str]:
    return {"detail": "Not implemented"}


@router.websocket("/ws")
async def websocket_stub(websocket: WebSocket) -> None:
    await websocket.accept()
    await websocket.send_json({"detail": "Not implemented"})
    await websocket.close()
