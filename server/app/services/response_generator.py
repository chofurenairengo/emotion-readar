"""応答生成サービス（統合）."""

from __future__ import annotations

import logging
import time
from datetime import datetime, timezone

from app.dto.audio import AudioFormat, TranscriptionResult
from app.dto.conversation import EmotionContext, Speaker
from app.dto.emotion import EmotionInterpretation
from app.dto.processing import AnalysisResponse
from app.services.conversation_service import ConversationService
from app.services.emotion_interpreter import EmotionInterpreterService
from app.services.llm_service import LLMService
from app.services.stt_service import STTService

logger = logging.getLogger(__name__)


class ResponseGeneratorService:
    """各サービスを統合して応答を生成."""

    def __init__(
        self,
        stt_service: STTService,
        conversation_service: ConversationService,
        emotion_interpreter: EmotionInterpreterService,
        llm_service: LLMService,
    ) -> None:
        """
        初期化.

        Args:
            stt_service: 音声認識サービス
            conversation_service: 会話履歴管理サービス
            emotion_interpreter: 感情解釈サービス
            llm_service: LLM推論サービス
        """
        self._stt = stt_service
        self._conversation = conversation_service
        self._emotion = emotion_interpreter
        self._llm = llm_service

    async def process(
        self,
        session_id: str,
        emotion_scores: dict[str, float],
        audio_data: bytes | None = None,
        audio_format: AudioFormat | None = None,
    ) -> AnalysisResponse:
        """
        メイン処理パイプライン.

        処理フロー:
        1. 音声 → STT（音声がある場合）
        2. 会話履歴に追加
        3. 感情スコア → 解釈
        4. LLM推論
        5. 結果統合

        Args:
            session_id: セッションID
            emotion_scores: 感情スコア（Kotlin側で算出）
            audio_data: 音声データ（オプション）
            audio_format: 音声フォーマット（オプション）

        Returns:
            AnalysisResponse: 統合された解析結果
        """
        start_time = time.perf_counter()
        logger.info(f"Processing request for session {session_id}")

        transcription: TranscriptionResult | None = None
        partner_utterance: str = ""

        if audio_data and audio_format:
            transcription = await self._transcribe_audio(audio_data, audio_format)
            if transcription:
                partner_utterance = transcription.text
                logger.debug(f"STT result: {transcription.text[:50]}...")

        if partner_utterance:
            self._update_conversation(session_id, partner_utterance, emotion_scores)

        emotion_interpretation = self._interpret_emotion(emotion_scores)

        conversation_context = self._conversation.get_recent_context(
            session_id, max_turns=10
        )

        logger.info(f"Calling LLM with {len(conversation_context)} context turns")

        llm_result = await self._llm.generate_responses(
            conversation_context=conversation_context,
            emotion_interpretation=emotion_interpretation,
            partner_last_utterance=partner_utterance or "(発話なし)",
        )

        processing_time_ms = int((time.perf_counter() - start_time) * 1000)

        logger.info(f"Processing completed in {processing_time_ms}ms")

        return AnalysisResponse(
            type="ANALYSIS_RESPONSE",
            timestamp=datetime.now(timezone.utc),
            emotion=emotion_interpretation,
            transcription=transcription,
            suggestions=llm_result.responses,
            situation_analysis=llm_result.situation_analysis,
            processing_time_ms=processing_time_ms,
        )

    async def _transcribe_audio(
        self,
        audio_data: bytes,
        audio_format: AudioFormat,
    ) -> TranscriptionResult | None:
        """
        音声をテキストに変換.

        失敗時はNoneを返し、処理は継続する。
        """
        try:
            return await self._stt.transcribe(
                audio_data=audio_data,
                format=audio_format,
                language="ja",
            )
        except Exception as e:
            logger.error(f"STT failed: {e}")
            return None

    def _update_conversation(
        self,
        session_id: str,
        text: str,
        emotion_scores: dict[str, float],
    ) -> None:
        """会話履歴に発話を追加."""
        try:
            primary_emotion = (
                max(emotion_scores, key=emotion_scores.get)  # type: ignore[arg-type]
                if emotion_scores
                else "neutral"
            )

            emotion_context = EmotionContext(
                primary_emotion=primary_emotion,
                emotion_scores=emotion_scores,
            )

            self._conversation.add_utterance(
                session_id=session_id,
                speaker=Speaker.PARTNER,
                text=text,
                emotion_context=emotion_context,
            )
        except Exception as e:
            logger.error(f"Failed to update conversation: {e}")

    def _interpret_emotion(
        self,
        emotion_scores: dict[str, float],
    ) -> EmotionInterpretation:
        """
        感情スコアを解釈.

        失敗時はデフォルト値（neutral）を返し、処理は継続する。
        """
        try:
            return self._emotion.interpret(emotion_scores)
        except Exception as e:
            logger.error(f"Emotion interpretation failed: {e}")
            return EmotionInterpretation(
                primary_emotion="neutral",
                intensity="medium",
                description="感情を解釈できませんでした",
            )
