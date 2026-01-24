"""LLMService の単体テスト."""

from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

import pytest

from app.core.exceptions import LLMError, LLMRateLimitError, LLMResponseParseError
from app.dto.conversation import Speaker, Utterance
from app.dto.emotion import EmotionInterpretation
from app.services.llm_service import LLMService


@pytest.fixture
def sample_context() -> list[Utterance]:
    """サンプルの会話コンテキスト."""
    return [
        Utterance(
            speaker=Speaker.USER,
            text="明日の会議の件で相談があります",
            timestamp=datetime.now(timezone.utc),
        ),
        Utterance(
            speaker=Speaker.PARTNER,
            text="はい、どのような相談でしょうか？",
            timestamp=datetime.now(timezone.utc),
        ),
    ]


@pytest.fixture
def sample_emotion() -> EmotionInterpretation:
    """サンプルの感情解釈."""
    return EmotionInterpretation(
        primary_emotion="confused",
        intensity="medium",
        description="相手は困惑しているようです",
        suggestion="説明を補足すると良いかもしれません",
    )


@pytest.fixture
def valid_llm_response() -> str:
    """有効なLLMレスポンス."""
    return """{
    "situation_analysis": "相手は会議の詳細を知りたがっています",
    "responses": [
        {
            "text": "明日の会議の開始時間を変更したいのですが、ご都合はいかがでしょうか？",
            "intent": "具体的な提案をする"
        },
        {
            "text": "すみません、時間調整をお願いできませんか？",
            "intent": "相手に配慮しながら提案する"
        }
    ]
}"""


@pytest.mark.asyncio
async def test_generate_responses_success(
    sample_context: list[Utterance],
    sample_emotion: EmotionInterpretation,
    valid_llm_response: str,
) -> None:
    """正常系: 応答生成が成功する."""
    with patch.object(
        LLMService, "_call_api", new_callable=AsyncMock
    ) as mock_api:
        mock_api.return_value = valid_llm_response

        service = LLMService(api_key="test-key")
        result = await service.generate_responses(
            conversation_context=sample_context,
            emotion_interpretation=sample_emotion,
            partner_last_utterance="はい、どのような相談でしょうか？",
        )

        assert len(result.responses) == 2
        assert result.responses[0].intent != result.responses[1].intent
        assert result.situation_analysis == "相手は会議の詳細を知りたがっています"
        mock_api.assert_called_once()


@pytest.mark.asyncio
async def test_generate_responses_with_markdown_json(
    sample_context: list[Utterance],
    sample_emotion: EmotionInterpretation,
) -> None:
    """マークダウンで囲まれたJSONをパースできる."""
    markdown_response = """```json
{
    "situation_analysis": "テスト分析",
    "responses": [
        {"text": "応答1", "intent": "意図A"},
        {"text": "応答2", "intent": "意図B"}
    ]
}
```"""

    with patch.object(
        LLMService, "_call_api", new_callable=AsyncMock
    ) as mock_api:
        mock_api.return_value = markdown_response

        service = LLMService(api_key="test-key")
        result = await service.generate_responses(
            conversation_context=sample_context,
            emotion_interpretation=sample_emotion,
            partner_last_utterance="テスト",
        )

        assert len(result.responses) == 2
        assert result.situation_analysis == "テスト分析"


@pytest.mark.asyncio
async def test_parse_response_invalid_json(
    sample_context: list[Utterance],
    sample_emotion: EmotionInterpretation,
) -> None:
    """不正なJSONでLLMResponseParseErrorが発生する."""
    with patch.object(
        LLMService, "_call_api", new_callable=AsyncMock
    ) as mock_api:
        mock_api.return_value = "これはJSONではありません"

        service = LLMService(api_key="test-key")
        with pytest.raises(LLMResponseParseError):
            await service.generate_responses(
                conversation_context=sample_context,
                emotion_interpretation=sample_emotion,
                partner_last_utterance="テスト",
            )


@pytest.mark.asyncio
async def test_parse_response_missing_key(
    sample_context: list[Utterance],
    sample_emotion: EmotionInterpretation,
) -> None:
    """必要なキーが欠けている場合にLLMResponseParseErrorが発生する."""
    incomplete_response = '{"situation_analysis": "分析のみ"}'

    with patch.object(
        LLMService, "_call_api", new_callable=AsyncMock
    ) as mock_api:
        mock_api.return_value = incomplete_response

        service = LLMService(api_key="test-key")
        with pytest.raises(LLMResponseParseError):
            await service.generate_responses(
                conversation_context=sample_context,
                emotion_interpretation=sample_emotion,
                partner_last_utterance="テスト",
            )


@pytest.mark.asyncio
async def test_parse_response_same_intent_validation_error(
    sample_context: list[Utterance],
    sample_emotion: EmotionInterpretation,
) -> None:
    """同じintentの応答でバリデーションエラーが発生する."""
    same_intent_response = """{
    "situation_analysis": "テスト",
    "responses": [
        {"text": "応答1", "intent": "同じ意図"},
        {"text": "応答2", "intent": "同じ意図"}
    ]
}"""

    with patch.object(
        LLMService, "_call_api", new_callable=AsyncMock
    ) as mock_api:
        mock_api.return_value = same_intent_response

        service = LLMService(api_key="test-key")
        with pytest.raises(LLMResponseParseError):
            await service.generate_responses(
                conversation_context=sample_context,
                emotion_interpretation=sample_emotion,
                partner_last_utterance="テスト",
            )


@pytest.mark.asyncio
async def test_retry_on_rate_limit(
    sample_context: list[Utterance],
    sample_emotion: EmotionInterpretation,
    valid_llm_response: str,
) -> None:
    """レートリミット時にリトライする."""
    call_count = 0

    async def mock_call_api(prompt: str) -> str:
        nonlocal call_count
        call_count += 1
        if call_count < 2:
            raise Exception("429 rate limit exceeded")
        return valid_llm_response

    with patch.object(
        LLMService, "_call_api", side_effect=mock_call_api
    ):
        service = LLMService(api_key="test-key")
        result = await service.generate_responses(
            conversation_context=sample_context,
            emotion_interpretation=sample_emotion,
            partner_last_utterance="テスト",
        )

        assert call_count == 2
        assert len(result.responses) == 2


@pytest.mark.asyncio
async def test_rate_limit_max_retries_exceeded(
    sample_context: list[Utterance],
    sample_emotion: EmotionInterpretation,
) -> None:
    """リトライ上限を超えるとLLMRateLimitErrorが発生する."""
    with patch.object(
        LLMService, "_call_api", new_callable=AsyncMock
    ) as mock_api:
        mock_api.side_effect = Exception("429 rate limit")

        service = LLMService(api_key="test-key")
        with pytest.raises(LLMRateLimitError):
            await service.generate_responses(
                conversation_context=sample_context,
                emotion_interpretation=sample_emotion,
                partner_last_utterance="テスト",
            )


@pytest.mark.asyncio
async def test_non_rate_limit_error_raises_immediately(
    sample_context: list[Utterance],
    sample_emotion: EmotionInterpretation,
) -> None:
    """レートリミット以外のエラーは即座にLLMErrorを発生させる."""
    with patch.object(
        LLMService, "_call_api", new_callable=AsyncMock
    ) as mock_api:
        mock_api.side_effect = Exception("Internal server error")

        service = LLMService(api_key="test-key")
        with pytest.raises(LLMError):
            await service.generate_responses(
                conversation_context=sample_context,
                emotion_interpretation=sample_emotion,
                partner_last_utterance="テスト",
            )


def test_build_prompt(
    sample_context: list[Utterance],
    sample_emotion: EmotionInterpretation,
) -> None:
    """プロンプト構築のテスト."""
    service = LLMService(api_key="test-key")
    prompt = service._build_prompt(
        context=sample_context,
        emotion=sample_emotion,
        last_utterance="はい、どのような相談でしょうか？",
    )

    assert "会話履歴" in prompt
    assert "明日の会議の件で相談があります" in prompt
    assert "感情状態" in prompt
    assert "confused" in prompt
    assert "medium" in prompt
    assert "相手の最後の発話" in prompt
    assert "はい、どのような相談でしょうか？" in prompt
