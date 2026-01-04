from app.dto.response import HealthResponse


def get_health() -> HealthResponse:
    return HealthResponse(status="ok")
