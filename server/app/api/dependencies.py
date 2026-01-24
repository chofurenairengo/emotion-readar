from typing import Any

from app.infra.repositories.in_memory_session_repo import InMemorySessionRepository
from app.services.connection_manager import ConnectionManager
from app.services.conversation_service import ConversationService
from app.services.emotion_interpreter import EmotionInterpreterService
from app.services.llm_service import LLMService
from app.services.response_generator import ResponseGeneratorService
from app.services.session_service import SessionService
from app.services.stt_service import STTService

_session_repository = InMemorySessionRepository()
_session_service = SessionService(_session_repository)
_connection_manager = ConnectionManager()
_response_generator: Any = None

_stt_service: STTService | None = None
_conversation_service: ConversationService | None = None
_emotion_interpreter: EmotionInterpreterService | None = None
_llm_service: LLMService | None = None
_response_generator: ResponseGeneratorService | None = None


def get_session_service() -> SessionService:
    return _session_service


def get_connection_manager() -> ConnectionManager:
    return _connection_manager


def get_stt_service() -> STTService:
    global _stt_service
    if _stt_service is None:
        _stt_service = STTService()
    return _stt_service


def get_conversation_service() -> ConversationService:
    global _conversation_service
    if _conversation_service is None:
        _conversation_service = ConversationService()
    return _conversation_service


def get_emotion_interpreter() -> EmotionInterpreterService:
    global _emotion_interpreter
    if _emotion_interpreter is None:
        _emotion_interpreter = EmotionInterpreterService()
    return _emotion_interpreter


def get_llm_service() -> LLMService:
    global _llm_service
    if _llm_service is None:
        _llm_service = LLMService()
    return _llm_service


def get_response_generator() -> ResponseGeneratorService:
    global _response_generator
    if _response_generator is None:
        _response_generator = ResponseGeneratorService(
            stt_service=get_stt_service(),
            conversation_service=get_conversation_service(),
            emotion_interpreter=get_emotion_interpreter(),
            llm_service=get_llm_service(),
        )
    return _response_generator
