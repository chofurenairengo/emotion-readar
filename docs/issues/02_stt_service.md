# STTサービスの実装

## 概要

音声データをテキストに変換する音声認識（Speech-to-Text）サービスを実装する。
**Google Cloud Speech-to-Text API** を使用。

## 期待する仕様

### ファイル

```
server/app/services/stt_service.py
```

### インターフェース

```python
from app.dto.audio import AudioFormat, TranscriptionResult


class STTService:
    """音声認識サービス（Google Cloud Speech-to-Text使用）"""

    def __init__(self) -> None:
        """
        初期化

        環境変数 GOOGLE_APPLICATION_CREDENTIALS に認証情報のパスを設定するか、
        Google Cloud SDKでログイン済みである必要があります。
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
            language: 言語コード（デフォルト日本語 "ja"）

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
```

### 例外クラス

```python
# app/core/exceptions.py

class STTError(Exception):
    """音声認識エラー"""
    pass
```

### 実装詳細

#### Google Cloud Speech-to-Text API

```python
from google.cloud import speech

class STTService:
    def __init__(self) -> None:
        self._client = speech.SpeechClient()

    async def transcribe(self, audio_data: bytes, ...) -> TranscriptionResult:
        audio = speech.RecognitionAudio(content=audio_data)
        config = speech.RecognitionConfig(
            encoding=speech.RecognitionConfig.AudioEncoding.LINEAR16,
            sample_rate_hertz=sample_rate,
            language_code="ja-JP",
            enable_automatic_punctuation=True,
        )
        response = self._client.recognize(config=config, audio=audio)
        # ...
```

### 音声フォーマット対応

| フォーマット | エンコーディング | 対応 |
|---|---|---|
| WAV | LINEAR16 | ✅ 対応 |
| PCM | LINEAR16 | ✅ 対応 |
| OPUS | OGG_OPUS | ✅ 対応 |

### 言語コードマッピング

| 入力 | Google Cloud形式 |
|---|---|
| `ja` | `ja-JP` |
| `en` | `en-US` |
| `ko` | `ko-KR` |
| `zh` | `zh-CN` |

### 依存関係（`pyproject.toml`）

```toml
dependencies = [
    "google-cloud-speech>=2.0.0",
]
```

### 環境変数

```bash
# サービスアカウントキーのパス
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json

# または、gcloud CLI でログイン
gcloud auth application-default login
```

### GCP設定手順

1. **Speech-to-Text APIを有効化**
   ```bash
   gcloud services enable speech.googleapis.com
   ```

2. **サービスアカウントを作成**
   ```bash
   gcloud iam service-accounts create stt-service \
     --display-name="STT Service Account"
   ```

3. **キーをダウンロード**
   ```bash
   gcloud iam service-accounts keys create key.json \
     --iam-account=stt-service@PROJECT_ID.iam.gserviceaccount.com
   ```

4. **環境変数を設定**
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
   ```

## 完了条件

- [x] `STTService`クラスが実装されている
- [x] WAV形式の音声を認識できる
- [x] PCM形式の音声を認識できる
- [x] OPUS形式の音声を認識できる
- [x] 日本語の音声を正しく認識できる
- [x] エラーハンドリングが実装されている
- [x] 単体テストが作成されている（10件）
- [x] `mypy`でエラーがない
- [x] `ruff`でエラーがない

## テストケース

```python
import pytest
from app.services.stt_service import STTService
from app.dto.audio import AudioFormat


@pytest.mark.asyncio
async def test_transcribe_japanese_wav():
    """日本語WAV音声の認識テスト"""
    service = STTService()
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

- 親Issue: #30 [Epic] サーバーサイドMVP実装
- 依存: #31 DTO定義
