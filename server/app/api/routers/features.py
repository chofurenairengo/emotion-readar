from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.auth import get_current_user
from app.api.dependencies import get_session_service
from app.core.exceptions import SessionPermissionError
from app.dto.feature import FeatureRequest, FeatureResponse
from app.services.feature_service import record_features
from app.services.session_service import SessionService

router = APIRouter(tags=["features"])


@router.post(
    "/features",
    response_model=FeatureResponse,
    status_code=status.HTTP_202_ACCEPTED,
)
def receive_features(
    payload: FeatureRequest,
    current_user: dict[str, Any] = Depends(get_current_user),
    session_service: SessionService = Depends(get_session_service),
) -> FeatureResponse:
    # 所有者検証
    session_id = payload.session_id
    if session_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="session_id is required",
        )
    try:
        session_service.verify_owner(session_id, current_user["uid"])
    except LookupError:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found",
        )
    except SessionPermissionError as exc:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=str(exc),
        )

    try:
        log = record_features(payload, session_service)
    except LookupError as exc:
        if str(exc) == "session_not_found":
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Session not found",
            ) from exc
        raise
    return FeatureResponse.from_model(log)
