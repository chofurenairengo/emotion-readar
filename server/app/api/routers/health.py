from fastapi import APIRouter, status

from app.dto.response import HealthResponse
from app.services.health_service import get_health

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse, status_code=status.HTTP_200_OK)
async def health_check() -> HealthResponse:
    return await get_health()
