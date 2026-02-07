"""音声認識（STT）サービス - Google Cloud Speech-to-Text."""

from __future__ import annotations

import io
import logging
import wave

from google.auth.credentials import Credentials
from google.cloud import speech

from app.core.exceptions import STTError
from app.dto.audio import AudioFormat, TranscriptionResult

logger = logging.getLogger(__name__)


class STTService:
    """音声認識サービス（Google Cloud Speech-to-Text使用）."""

    def __init__(self, credentials: Credentials | None = None) -> None:
        """
        初期化.

        Args:
            credentials: GCP認証情報。Noneの場合はADCが自動検出される。
        """
        try:
            self._client = speech.SpeechClient(credentials=credentials)
        except Exception as e:
            raise STTError(f"Failed to initialize Speech-to-Text client: {e}") from e

    async def transcribe(
        self,
        audio_data: bytes,
        format: AudioFormat,
        sample_rate: int = 16000,
        language: str = "ja",
    ) -> TranscriptionResult:
        """
        音声データをテキストに変換.

        Args:
            audio_data: 音声のバイナリデータ
            format: 音声フォーマット (wav, opus, pcm)
            sample_rate: サンプリングレート（デフォルト16000Hz）
            language: 言語コード（デフォルト日本語 "ja"）

        Returns:
            TranscriptionResult: 認識結果

        Raises:
            STTError: 音声認識に失敗した場合
        """
        try:
            # エンコーディングとサンプルレートを決定
            encoding, actual_sample_rate = self._get_encoding_config(
                audio_data, format, sample_rate
            )

            # 音声の長さを取得
            duration_ms = self._get_audio_duration_ms(audio_data, format, sample_rate)

            # Google Cloud Speech-to-Text APIを呼び出し
            audio = speech.RecognitionAudio(content=audio_data)
            config = speech.RecognitionConfig(
                encoding=encoding,
                sample_rate_hertz=actual_sample_rate,
                language_code=self._to_language_code(language),
                enable_automatic_punctuation=True,
            )

            response = self._client.recognize(config=config, audio=audio)

            # 結果を集約
            text = ""
            confidence = 0.0
            result_count = 0

            for result in response.results:
                if result.alternatives:
                    best = result.alternatives[0]
                    text += best.transcript
                    confidence += best.confidence
                    result_count += 1

            if result_count > 0:
                confidence /= result_count
            else:
                confidence = 0.0

            logger.info(f"STT completed: {len(text)} chars, {duration_ms}ms audio")

            return TranscriptionResult(
                text=text.strip(),
                confidence=confidence,
                language=language,
                duration_ms=duration_ms,
            )

        except Exception as e:
            logger.error(f"STT error: {e}")
            raise STTError(f"Transcription failed: {e}") from e

    def _get_encoding_config(
        self,
        audio_data: bytes,
        format: AudioFormat,
        sample_rate: int,
    ) -> tuple[int, int]:
        """
        音声フォーマットに応じたエンコーディング設定を取得.

        Args:
            audio_data: 音声データ
            format: 音声フォーマット
            sample_rate: サンプリングレート

        Returns:
            (エンコーディング, サンプルレート) のタプル
        """
        if format == AudioFormat.WAV:
            # WAVファイルからサンプルレートを読み取る
            actual_sample_rate = self._get_wav_sample_rate(audio_data) or sample_rate
            return speech.RecognitionConfig.AudioEncoding.LINEAR16, actual_sample_rate
        elif format == AudioFormat.PCM:
            return speech.RecognitionConfig.AudioEncoding.LINEAR16, sample_rate
        elif format == AudioFormat.OPUS:
            return speech.RecognitionConfig.AudioEncoding.OGG_OPUS, sample_rate
        else:
            raise STTError(f"Unsupported audio format: {format}")

    def _get_wav_sample_rate(self, wav_data: bytes) -> int | None:
        """WAVデータからサンプルレートを取得."""
        try:
            buffer = io.BytesIO(wav_data)
            with wave.open(buffer, "rb") as wav_file:
                return wav_file.getframerate()
        except Exception:
            return None

    def _get_audio_duration_ms(
        self,
        audio_data: bytes,
        format: AudioFormat,
        sample_rate: int,
    ) -> int:
        """
        音声データから長さを取得.

        Args:
            audio_data: 音声データ
            format: 音声フォーマット
            sample_rate: サンプリングレート

        Returns:
            音声の長さ（ミリ秒）
        """
        try:
            if format == AudioFormat.WAV:
                buffer = io.BytesIO(audio_data)
                with wave.open(buffer, "rb") as wav_file:
                    frames = wav_file.getnframes()
                    rate = wav_file.getframerate()
                    duration_sec = frames / float(rate)
                    return int(duration_sec * 1000)
            elif format == AudioFormat.PCM:
                # 16bit mono PCMと仮定
                num_samples = len(audio_data) // 2
                duration_sec = num_samples / sample_rate
                return int(duration_sec * 1000)
            else:
                return 0
        except Exception:
            return 0

    def _to_language_code(self, language: str) -> str:
        """
        言語コードをGoogle Cloud形式に変換.

        Args:
            language: 言語コード ("ja", "en" など)

        Returns:
            Google Cloud形式の言語コード ("ja-JP", "en-US" など)
        """
        language_map = {
            "ja": "ja-JP",
            "en": "en-US",
            "zh": "zh-CN",
            "ko": "ko-KR",
            "fr": "fr-FR",
            "de": "de-DE",
            "es": "es-ES",
            "it": "it-IT",
            "pt": "pt-BR",
        }
        return language_map.get(language, f"{language}-{language.upper()}")
