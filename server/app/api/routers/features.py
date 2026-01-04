from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies import get_session_service
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
    session_service: SessionService = Depends(get_session_service),
) -> FeatureResponse:
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
