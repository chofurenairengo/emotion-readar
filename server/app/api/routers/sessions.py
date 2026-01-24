from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.auth import get_current_user
from app.api.dependencies import get_session_service
from app.dto.session import SessionResponse
from app.services.session_service import SessionService

router = APIRouter(tags=["sessions"])


# セッション作成
@router.post(
    "/sessions",
    response_model=SessionResponse,
    status_code=status.HTTP_201_CREATED,
)
def start_session(
    current_user: dict[str, Any] = Depends(get_current_user),
    session_service: SessionService = Depends(get_session_service),
) -> SessionResponse:
    session = session_service.create_session()
    return SessionResponse.from_model(session)


# セッション終了
@router.post(
    "/sessions/{session_id}/end",
    response_model=SessionResponse,
    status_code=status.HTTP_200_OK,
)
def finish_session(
    session_id: str,
    current_user: dict[str, Any] = Depends(get_current_user),
    session_service: SessionService = Depends(get_session_service),
) -> SessionResponse:
    session = session_service.end_session(session_id)
    if session is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    return SessionResponse.from_model(session)


# セッションを表示
@router.get(
    "/sessions/{session_id}",
    response_model=SessionResponse,
    status_code=status.HTTP_200_OK,
)
def read_session(
    session_id: str,
    current_user: dict[str, Any] = Depends(get_current_user),
    session_service: SessionService = Depends(get_session_service),
) -> SessionResponse:
    session = session_service.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    return SessionResponse.from_model(session)
