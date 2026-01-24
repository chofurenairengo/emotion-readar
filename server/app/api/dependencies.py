from typing import Any

from app.infra.repositories.in_memory_session_repo import InMemorySessionRepository
from app.services.connection_manager import ConnectionManager
from app.services.session_service import SessionService

_session_repository = InMemorySessionRepository()
_session_service = SessionService(_session_repository)
_connection_manager = ConnectionManager()
_response_generator: Any = None


def get_session_service() -> SessionService:
    return _session_service


def get_connection_manager() -> ConnectionManager:
    return _connection_manager


def get_response_generator() -> Any:
    """ResponseGeneratorServiceを取得.

    Note:
        ResponseGeneratorServiceの実装完了後、正しい型を返すように更新してください。
        現在はプレースホルダーとしてNoneを返します。
    """
    global _response_generator
    if _response_generator is None:
        # TODO: ResponseGeneratorServiceが実装されたら、ここでインスタンスを作成
        # _response_generator = ResponseGeneratorService(...)
        raise NotImplementedError(
            "ResponseGeneratorService is not yet implemented. "
            "Please implement issue #36 first."
        )
    return _response_generator
