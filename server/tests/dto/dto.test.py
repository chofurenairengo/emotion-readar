"""DTO定義のテスト."""

from datetime import datetime, timezone

import pytest

from app.dto.audio import AudioFormat, TranscriptionResult
from app.dto.conversation import EmotionContext, Speaker, Utterance
from app.dto.emotion import EmotionChange, EmotionInterpretation
from app.dto.llm import LLMResponseResult, ResponseSuggestion
from app.dto.processing import AnalysisRequest, AnalysisResponse


class TestAudioDTO:
    """audio.py のテスト."""

    def test_audio_format_enum(self) -> None:
        """AudioFormatのEnum値テスト."""
        assert AudioFormat.WAV == "wav"
        assert AudioFormat.OPUS == "opus"
        assert AudioFormat.PCM == "pcm"

    def test_transcription_result(self) -> None:
        """TranscriptionResultのテスト."""
        result = TranscriptionResult(
            text="こんにちは",
            confidence=0.95,
            language="ja",
            duration_ms=1500,
        )
        assert result.text == "こんにちは"
        assert result.confidence == 0.95
        assert result.language == "ja"
        assert result.duration_ms == 1500


class TestConversationDTO:
    """conversation.py のテスト."""

    def test_speaker_enum(self) -> None:
        """Speakerのテスト."""
        assert Speaker.USER == "user"
        assert Speaker.PARTNER == "partner"

    def test_emotion_context(self) -> None:
        """EmotionContextのテスト."""
        ctx = EmotionContext(
            primary_emotion="happy",
            emotion_scores={"happy": 0.8, "neutral": 0.2},
        )
        assert ctx.primary_emotion == "happy"
        assert ctx.emotion_scores["happy"] == 0.8

    def test_utterance(self) -> None:
        """Utteranceのテスト."""
        now = datetime.now(timezone.utc)
        utterance = Utterance(
            speaker=Speaker.USER,
            text="こんにちは",
            timestamp=now,
        )
        assert utterance.speaker == Speaker.USER
        assert utterance.text == "こんにちは"
        assert utterance.emotion_context is None

    def test_utterance_with_emotion(self) -> None:
        """感情コンテキスト付きUtteranceのテスト."""
        now = datetime.now(timezone.utc)
        ctx = EmotionContext(
            primary_emotion="confused",
            emotion_scores={"confused": 0.7},
        )
        utterance = Utterance(
            speaker=Speaker.PARTNER,
            text="どういう意味ですか？",
            timestamp=now,
            emotion_context=ctx,
        )
        assert utterance.emotion_context is not None
        assert utterance.emotion_context.primary_emotion == "confused"


class TestEmotionDTO:
    """emotion.py のテスト."""

    def test_emotion_interpretation(self) -> None:
        """EmotionInterpretationのテスト."""
        interpretation = EmotionInterpretation(
            primary_emotion="confused",
            intensity="high",
            description="相手は困惑しているようです",
            suggestion="説明を補足すると良いかもしれません",
        )
        assert interpretation.primary_emotion == "confused"
        assert interpretation.intensity == "high"
        assert "困惑" in interpretation.description
        assert interpretation.suggestion is not None

    def test_emotion_interpretation_without_suggestion(self) -> None:
        """suggestion無しのEmotionInterpretationテスト."""
        interpretation = EmotionInterpretation(
            primary_emotion="neutral",
            intensity="medium",
            description="相手は平静です",
        )
        assert interpretation.suggestion is None

    def test_emotion_change(self) -> None:
        """EmotionChangeのテスト."""
        change = EmotionChange(
            from_emotion="happy",
            to_emotion="confused",
            description="相手の表情が曇りました",
        )
        assert change.from_emotion == "happy"
        assert change.to_emotion == "confused"


class TestLLMDTO:
    """llm.py のテスト."""

    def test_response_suggestion(self) -> None:
        """ResponseSuggestionのテスト."""
        suggestion = ResponseSuggestion(
            text="申し訳ありません。もう少し詳しくご説明いたします。",
            tone="formal",
            intent="説明を補足する",
        )
        assert suggestion.tone == "formal"
        assert suggestion.intent == "説明を補足する"

    def test_llm_response_result(self) -> None:
        """LLMResponseResultのテスト."""
        result = LLMResponseResult(
            responses=[
                ResponseSuggestion(text="応答1", tone="formal", intent="テスト"),
                ResponseSuggestion(text="応答2", tone="casual", intent="テスト"),
                ResponseSuggestion(text="応答3", tone="empathetic", intent="テスト"),
            ],
            situation_analysis="テスト分析",
        )
        assert len(result.responses) == 3
        assert result.situation_analysis == "テスト分析"


class TestProcessingDTO:
    """processing.py のテスト."""

    def test_analysis_request(self) -> None:
        """AnalysisRequestのテスト."""
        now = datetime.now(timezone.utc)
        request = AnalysisRequest(
            session_id="test-session-123",
            timestamp=now,
            emotion_scores={"happy": 0.1, "confused": 0.8},
        )
        assert request.type == "ANALYSIS_REQUEST"
        assert request.session_id == "test-session-123"
        assert request.audio_data is None

    def test_analysis_request_with_audio(self) -> None:
        """音声データ付きAnalysisRequestのテスト."""
        now = datetime.now(timezone.utc)
        request = AnalysisRequest(
            session_id="test-session-123",
            timestamp=now,
            emotion_scores={"neutral": 0.9},
            audio_data="base64encodeddata",
            audio_format="wav",
        )
        assert request.audio_data == "base64encodeddata"
        assert request.audio_format == "wav"

    def test_analysis_response(self) -> None:
        """AnalysisResponseのテスト."""
        now = datetime.now(timezone.utc)
        response = AnalysisResponse(
            timestamp=now,
            emotion=EmotionInterpretation(
                primary_emotion="confused",
                intensity="high",
                description="相手は困惑しています",
            ),
            suggestions=[
                ResponseSuggestion(text="応答1", tone="formal", intent="テスト"),
                ResponseSuggestion(text="応答2", tone="casual", intent="テスト"),
                ResponseSuggestion(text="応答3", tone="empathetic", intent="テスト"),
            ],
            situation_analysis="状況分析テスト",
            processing_time_ms=850,
        )
        assert response.type == "ANALYSIS_RESPONSE"
        assert response.transcription is None
        assert len(response.suggestions) == 3
        assert response.processing_time_ms == 850

    def test_analysis_response_with_transcription(self) -> None:
        """STT結果付きAnalysisResponseのテスト."""
        now = datetime.now(timezone.utc)
        response = AnalysisResponse(
            timestamp=now,
            emotion=EmotionInterpretation(
                primary_emotion="neutral",
                intensity="medium",
                description="平静です",
            ),
            transcription=TranscriptionResult(
                text="こんにちは",
                confidence=0.95,
                language="ja",
                duration_ms=1000,
            ),
            suggestions=[
                ResponseSuggestion(text="応答", tone="formal", intent="テスト"),
            ],
            situation_analysis="分析",
            processing_time_ms=500,
        )
        assert response.transcription is not None
        assert response.transcription.text == "こんにちは"
