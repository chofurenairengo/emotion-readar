from app.infra.repositories.in_memory_session_repo import InMemorySessionRepository
from app.services.connection_manager import ConnectionManager
from app.services.session_service import SessionService

_session_repository = InMemorySessionRepository()
_session_service = SessionService(_session_repository)
_connection_manager = ConnectionManager()


def get_session_service() -> SessionService:
    return _session_service


def get_connection_manager() -> ConnectionManager:
    return _connection_manager
