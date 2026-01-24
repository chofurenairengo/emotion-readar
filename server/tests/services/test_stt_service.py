"""STTサービスのテスト."""

import io
import wave
from unittest.mock import MagicMock, patch

import pytest

from app.core.exceptions import STTError
from app.dto.audio import AudioFormat


def create_test_wav(duration_sec: float = 1.0, sample_rate: int = 16000) -> bytes:
    """テスト用のWAVデータを生成."""
    num_samples = int(duration_sec * sample_rate)
    # 無音のPCMデータ
    pcm_data = b"\x00\x00" * num_samples

    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        wav_file.writeframes(pcm_data)
    return buffer.getvalue()


class TestSTTService:
    """STTServiceのテスト."""

    def test_init_success(self) -> None:
        """初期化成功テスト."""
        with patch("app.services.stt_service.speech.SpeechClient"):
            from app.services.stt_service import STTService

            service = STTService()
            assert service._client is not None

    def test_init_failure(self) -> None:
        """初期化失敗テスト."""
        with patch(
            "app.services.stt_service.speech.SpeechClient",
            side_effect=Exception("Connection failed"),
        ):
            from app.services.stt_service import STTService

            with pytest.raises(STTError, match="Failed to initialize"):
                STTService()

    @pytest.mark.asyncio
    async def test_transcribe_success(self) -> None:
        """音声認識成功テスト."""
        with patch("app.services.stt_service.speech.SpeechClient") as mock_client_cls:
            mock_client = MagicMock()
            mock_client_cls.return_value = mock_client

            # モックレスポンスを作成
            mock_alternative = MagicMock()
            mock_alternative.transcript = "こんにちは"
            mock_alternative.confidence = 0.95

            mock_result = MagicMock()
            mock_result.alternatives = [mock_alternative]

            mock_response = MagicMock()
            mock_response.results = [mock_result]

            mock_client.recognize.return_value = mock_response

            from app.services.stt_service import STTService

            service = STTService()
            wav_data = create_test_wav(duration_sec=1.0)

            result = await service.transcribe(
                audio_data=wav_data,
                format=AudioFormat.WAV,
                language="ja",
            )

            assert result.text == "こんにちは"
            assert result.confidence == 0.95
            assert result.language == "ja"
            assert result.duration_ms == 1000

    @pytest.mark.asyncio
    async def test_transcribe_pcm_format(self) -> None:
        """PCMフォーマットの音声認識テスト."""
        with patch("app.services.stt_service.speech.SpeechClient") as mock_client_cls:
            mock_client = MagicMock()
            mock_client_cls.return_value = mock_client

            mock_alternative = MagicMock()
            mock_alternative.transcript = "テスト"
            mock_alternative.confidence = 0.9

            mock_result = MagicMock()
            mock_result.alternatives = [mock_alternative]

            mock_response = MagicMock()
            mock_response.results = [mock_result]

            mock_client.recognize.return_value = mock_response

            from app.services.stt_service import STTService

            service = STTService()
            # 1秒分のPCMデータ（16kHz, 16bit mono）
            pcm_data = b"\x00\x00" * 16000

            result = await service.transcribe(
                audio_data=pcm_data,
                format=AudioFormat.PCM,
                sample_rate=16000,
                language="ja",
            )

            assert result.text == "テスト"
            assert result.duration_ms == 1000

    @pytest.mark.asyncio
    async def test_transcribe_opus_format(self) -> None:
        """OPUSフォーマットの音声認識テスト."""
        with patch("app.services.stt_service.speech.SpeechClient") as mock_client_cls:
            with patch("app.services.stt_service.speech") as mock_speech:
                mock_client = MagicMock()
                mock_client_cls.return_value = mock_client

                mock_alternative = MagicMock()
                mock_alternative.transcript = "オーパス"
                mock_alternative.confidence = 0.88

                mock_result = MagicMock()
                mock_result.alternatives = [mock_alternative]

                mock_response = MagicMock()
                mock_response.results = [mock_result]

                mock_client.recognize.return_value = mock_response

                # OGG_OPUS エンコーディングをモック
                mock_speech.RecognitionConfig.AudioEncoding.OGG_OPUS = "OGG_OPUS"
                mock_speech.RecognitionConfig.AudioEncoding.LINEAR16 = "LINEAR16"
                mock_speech.RecognitionAudio.return_value = MagicMock()
                mock_speech.RecognitionConfig.return_value = MagicMock()
                mock_speech.SpeechClient = mock_client_cls

                from app.services.stt_service import STTService

                service = STTService()

                result = await service.transcribe(
                    audio_data=b"fake-opus-data",
                    format=AudioFormat.OPUS,
                    sample_rate=16000,
                    language="ja",
                )

                assert result.text == "オーパス"

    @pytest.mark.asyncio
    async def test_transcribe_empty_result(self) -> None:
        """空の認識結果テスト."""
        with patch("app.services.stt_service.speech.SpeechClient") as mock_client_cls:
            mock_client = MagicMock()
            mock_client_cls.return_value = mock_client

            mock_response = MagicMock()
            mock_response.results = []

            mock_client.recognize.return_value = mock_response

            from app.services.stt_service import STTService

            service = STTService()
            wav_data = create_test_wav()

            result = await service.transcribe(
                audio_data=wav_data,
                format=AudioFormat.WAV,
                language="ja",
            )

            assert result.text == ""
            assert result.confidence == 0.0

    @pytest.mark.asyncio
    async def test_transcribe_api_error(self) -> None:
        """API呼び出しエラーテスト."""
        with patch("app.services.stt_service.speech.SpeechClient") as mock_client_cls:
            mock_client = MagicMock()
            mock_client_cls.return_value = mock_client
            mock_client.recognize.side_effect = Exception("API Error")

            from app.services.stt_service import STTService

            service = STTService()
            wav_data = create_test_wav()

            with pytest.raises(STTError, match="Transcription failed"):
                await service.transcribe(
                    audio_data=wav_data,
                    format=AudioFormat.WAV,
                    language="ja",
                )

    def test_to_language_code(self) -> None:
        """言語コード変換テスト."""
        with patch("app.services.stt_service.speech.SpeechClient"):
            from app.services.stt_service import STTService

            service = STTService()

            assert service._to_language_code("ja") == "ja-JP"
            assert service._to_language_code("en") == "en-US"
            assert service._to_language_code("ko") == "ko-KR"
            assert service._to_language_code("xx") == "xx-XX"

    def test_get_audio_duration_wav(self) -> None:
        """WAV音声長取得テスト."""
        with patch("app.services.stt_service.speech.SpeechClient"):
            from app.services.stt_service import STTService

            service = STTService()

            # 2秒のWAVデータ
            wav_data = create_test_wav(duration_sec=2.0)
            duration_ms = service._get_audio_duration_ms(
                wav_data, AudioFormat.WAV, 16000
            )

            assert duration_ms == 2000

    def test_get_audio_duration_pcm(self) -> None:
        """PCM音声長取得テスト."""
        with patch("app.services.stt_service.speech.SpeechClient"):
            from app.services.stt_service import STTService

            service = STTService()

            # 1秒分のPCMデータ（16kHz, 16bit mono = 32000 bytes）
            pcm_data = b"\x00\x00" * 16000
            duration_ms = service._get_audio_duration_ms(
                pcm_data, AudioFormat.PCM, 16000
            )

            assert duration_ms == 1000
