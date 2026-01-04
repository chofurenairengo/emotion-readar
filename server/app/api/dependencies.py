from app.infra.repositories.in_memory_session_repo import InMemorySessionRepository
from app.services.session_service import SessionService

_session_repository = InMemorySessionRepository()
_session_service = SessionService(_session_repository)


def get_session_service() -> SessionService:
    return _session_service
