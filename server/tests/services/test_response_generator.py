"""応答生成サービス（統合）のテスト."""

from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.dto.audio import AudioFormat, TranscriptionResult
from app.dto.conversation import Speaker, Utterance
from app.dto.emotion import EmotionInterpretation
from app.dto.llm import LLMResponseResult, ResponseSuggestion
from app.services.response_generator import ResponseGeneratorService


@pytest.fixture
def mock_services() -> tuple[MagicMock, MagicMock, MagicMock, MagicMock]:
    """モックサービスのフィクスチャ."""
    stt = MagicMock()
    stt.transcribe = AsyncMock(
        return_value=TranscriptionResult(
            text="こんにちは",
            confidence=0.95,
            language="ja",
            duration_ms=1000,
        )
    )

    conversation = MagicMock()
    conversation.get_recent_context.return_value = []
    conversation.add_utterance.return_value = None

    emotion = MagicMock()
    emotion.interpret.return_value = EmotionInterpretation(
        primary_emotion="neutral",
        intensity="medium",
        description="相手は平静です",
    )

    llm = MagicMock()
    llm.generate_responses = AsyncMock(
        return_value=LLMResponseResult(
            responses=[
                ResponseSuggestion(text="応答1", intent="テスト1"),
                ResponseSuggestion(text="応答2", intent="テスト2"),
            ],
            situation_analysis="テスト分析",
        )
    )

    return stt, conversation, emotion, llm


@pytest.mark.asyncio
async def test_process_with_audio(
    mock_services: tuple[MagicMock, MagicMock, MagicMock, MagicMock],
) -> None:
    """音声ありの処理テスト."""
    stt, conversation, emotion, llm = mock_services
    service = ResponseGeneratorService(stt, conversation, emotion, llm)

    result = await service.process(
        session_id="test-session",
        emotion_scores={"neutral": 0.8},
        audio_data=b"fake-audio",
        audio_format=AudioFormat.WAV,
    )

    assert result.type == "ANALYSIS_RESPONSE"
    assert result.transcription is not None
    assert result.transcription.text == "こんにちは"
    assert len(result.suggestions) == 2
    assert result.processing_time_ms >= 0

    stt.transcribe.assert_called_once()
    conversation.add_utterance.assert_called_once()
    emotion.interpret.assert_called_once()
    llm.generate_responses.assert_called_once()


@pytest.mark.asyncio
async def test_process_without_audio(
    mock_services: tuple[MagicMock, MagicMock, MagicMock, MagicMock],
) -> None:
    """音声なしの処理テスト."""
    stt, conversation, emotion, llm = mock_services
    service = ResponseGeneratorService(stt, conversation, emotion, llm)

    result = await service.process(
        session_id="test-session",
        emotion_scores={"confused": 0.7},
    )

    assert result.transcription is None
    assert len(result.suggestions) == 2

    stt.transcribe.assert_not_called()
    conversation.add_utterance.assert_not_called()


@pytest.mark.asyncio
async def test_process_stt_failure(
    mock_services: tuple[MagicMock, MagicMock, MagicMock, MagicMock],
) -> None:
    """STT失敗時の処理継続テスト."""
    stt, conversation, emotion, llm = mock_services
    stt.transcribe = AsyncMock(side_effect=Exception("STT failed"))

    service = ResponseGeneratorService(stt, conversation, emotion, llm)

    result = await service.process(
        session_id="test-session",
        emotion_scores={"neutral": 0.8},
        audio_data=b"fake-audio",
        audio_format=AudioFormat.WAV,
    )

    assert result.transcription is None
    assert len(result.suggestions) == 2
    llm.generate_responses.assert_called_once()


@pytest.mark.asyncio
async def test_process_with_conversation_context(
    mock_services: tuple[MagicMock, MagicMock, MagicMock, MagicMock],
) -> None:
    """会話コンテキストありの処理テスト."""
    stt, conversation, emotion, llm = mock_services

    conversation.get_recent_context.return_value = [
        Utterance(
            speaker=Speaker.USER,
            text="前の発話",
            timestamp=datetime.now(timezone.utc),
        ),
    ]

    service = ResponseGeneratorService(stt, conversation, emotion, llm)

    await service.process(
        session_id="test-session",
        emotion_scores={"happy": 0.6},
    )

    call_args = llm.generate_responses.call_args
    assert len(call_args.kwargs["conversation_context"]) == 1


@pytest.mark.asyncio
async def test_process_emotion_interpretation(
    mock_services: tuple[MagicMock, MagicMock, MagicMock, MagicMock],
) -> None:
    """感情解釈の呼び出しテスト."""
    stt, conversation, emotion, llm = mock_services

    emotion.interpret.return_value = EmotionInterpretation(
        primary_emotion="confused",
        intensity="high",
        description="相手は困惑しています",
        suggestion="説明を補足してください",
    )

    service = ResponseGeneratorService(stt, conversation, emotion, llm)

    result = await service.process(
        session_id="test-session",
        emotion_scores={"confused": 0.9},
    )

    assert result.emotion.primary_emotion == "confused"
    assert result.emotion.intensity == "high"


@pytest.mark.asyncio
async def test_process_llm_failure(
    mock_services: tuple[MagicMock, MagicMock, MagicMock, MagicMock],
) -> None:
    """LLM失敗時の例外伝播テスト."""
    stt, conversation, emotion, llm = mock_services
    llm.generate_responses = AsyncMock(side_effect=Exception("LLM failed"))

    service = ResponseGeneratorService(stt, conversation, emotion, llm)

    with pytest.raises(Exception, match="LLM failed"):
        await service.process(
            session_id="test-session",
            emotion_scores={"neutral": 0.8},
        )
