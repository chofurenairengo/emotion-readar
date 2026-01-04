from app.dto.health import HealthResponse


def get_health() -> HealthResponse:
    return HealthResponse(status="ok")
