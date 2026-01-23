# STTサービスの実装

## 概要

音声データをテキストに変換する音声認識（Speech-to-Text）サービスを実装する。

## 期待する仕様

### ファイル

```
server/app/services/stt_service.py
```

### インターフェース

```python
from app.dto.audio import AudioFormat, TranscriptionResult


class STTService:
    """音声認識サービス"""

    def __init__(self, api_key: str | None = None):
        """
        初期化

        Args:
            api_key: STTサービスのAPIキー（Whisper API使用時）
        """
        pass

    async def transcribe(
        self,
        audio_data: bytes,
        format: AudioFormat,
        sample_rate: int = 16000,
        language: str = "ja",
    ) -> TranscriptionResult:
        """
        音声データをテキストに変換

        Args:
            audio_data: 音声のバイナリデータ
            format: 音声フォーマット (wav, opus, pcm)
            sample_rate: サンプリングレート（デフォルト16000Hz）
            language: 言語ヒント（デフォルト日本語）

        Returns:
            TranscriptionResult:
                text: 認識されたテキスト
                confidence: 信頼度 (0.0-1.0)
                language: 検出された言語
                duration_ms: 音声の長さ

        Raises:
            STTError: 音声認識に失敗した場合
        """
        pass

    def _decode_audio(
        self,
        audio_data: bytes,
        format: AudioFormat,
    ) -> bytes:
        """
        音声データをデコード（必要に応じてWAVに変換）

        Args:
            audio_data: 入力音声データ
            format: 入力フォーマット

        Returns:
            デコードされた音声データ（WAV形式）
        """
        pass
```

### 例外クラス

```python
# app/core/exceptions.py に追加

class STTError(Exception):
    """音声認識エラー"""
    pass
```

### 実装オプション

MVP段階では以下のいずれかを選択：

#### オプション1: OpenAI Whisper API（推奨）
```python
import openai

class STTService:
    def __init__(self, api_key: str):
        self.client = openai.OpenAI(api_key=api_key)

    async def transcribe(self, audio_data: bytes, ...) -> TranscriptionResult:
        response = self.client.audio.transcriptions.create(
            model="whisper-1",
            file=audio_data,
            language=language,
        )
        return TranscriptionResult(
            text=response.text,
            confidence=1.0,  # Whisper APIは信頼度を返さない
            language=language,
            duration_ms=...,
        )
```

#### オプション2: Google Cloud Speech-to-Text
```python
from google.cloud import speech

class STTService:
    def __init__(self):
        self.client = speech.SpeechClient()

    async def transcribe(self, audio_data: bytes, ...) -> TranscriptionResult:
        audio = speech.RecognitionAudio(content=audio_data)
        config = speech.RecognitionConfig(
            encoding=speech.RecognitionConfig.AudioEncoding.LINEAR16,
            sample_rate_hertz=sample_rate,
            language_code="ja-JP",
        )
        response = self.client.recognize(config=config, audio=audio)
        # ...
```

#### オプション3: faster-whisper（ローカル実行）
```python
from faster_whisper import WhisperModel

class STTService:
    def __init__(self, model_size: str = "base"):
        self.model = WhisperModel(model_size, device="cpu")

    async def transcribe(self, audio_data: bytes, ...) -> TranscriptionResult:
        segments, info = self.model.transcribe(audio_data, language=language)
        text = "".join([segment.text for segment in segments])
        return TranscriptionResult(
            text=text,
            confidence=info.language_probability,
            language=info.language,
            duration_ms=int(info.duration * 1000),
        )
```

### 音声フォーマット対応

| フォーマット | 説明 | 対応 |
|---|---|---|
| WAV | 非圧縮PCM | 必須 |
| OPUS | 圧縮（WebRTC標準） | 推奨 |
| PCM | 生データ | オプション |

### 依存関係追加（`pyproject.toml`）

Whisper API使用時:
```toml
dependencies = [
    "openai>=1.0.0",
]
```

faster-whisper使用時:
```toml
dependencies = [
    "faster-whisper>=0.10.0",
]
```

Google Cloud使用時:
```toml
dependencies = [
    "google-cloud-speech>=2.0.0",
]
```

### 環境変数

```
# Whisper API
OPENAI_API_KEY=sk-...

# Google Cloud
GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials.json
```

## 完了条件

- [ ] `STTService`クラスが実装されている
- [ ] WAV形式の音声を認識できる
- [ ] 日本語の音声を正しく認識できる
- [ ] エラーハンドリングが実装されている
- [ ] 単体テストが作成されている
- [ ] `mypy`でエラーがない

## テストケース

```python
import pytest
from app.services.stt_service import STTService
from app.dto.audio import AudioFormat


@pytest.mark.asyncio
async def test_transcribe_japanese_wav():
    """日本語WAV音声の認識テスト"""
    service = STTService(api_key="...")
    with open("tests/fixtures/hello_japanese.wav", "rb") as f:
        audio_data = f.read()

    result = await service.transcribe(
        audio_data=audio_data,
        format=AudioFormat.WAV,
        language="ja",
    )

    assert result.text != ""
    assert result.language == "ja"
    assert 0.0 <= result.confidence <= 1.0
```

## 関連Issue

- 親Issue: サーバーサイドMVP実装（Epic）
- 依存: #1 DTO定義
