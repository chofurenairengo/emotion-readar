# DTO定義（型定義）の実装

## 概要

サーバーサイドMVPで使用する全てのDTO（Data Transfer Object）を定義する。

## 期待する仕様

### 作成するファイル

```
server/app/dto/
├── audio.py          # 音声関連
├── conversation.py   # 会話履歴
├── emotion.py        # 感情解釈
├── llm.py            # LLM応答
└── processing.py     # 統合結果
```

### 1. `dto/audio.py` - 音声関連

```python
from enum import Enum
from pydantic import BaseModel


class AudioFormat(str, Enum):
    """対応音声フォーマット"""
    WAV = "wav"
    OPUS = "opus"
    PCM = "pcm"


class TranscriptionResult(BaseModel):
    """STT結果"""
    text: str                    # 認識されたテキスト
    confidence: float            # 信頼度 (0.0-1.0)
    language: str                # 検出言語 ("ja", "en")
    duration_ms: int             # 音声の長さ（ミリ秒）
```

### 2. `dto/conversation.py` - 会話履歴

```python
from datetime import datetime
from enum import Enum
from pydantic import BaseModel


class Speaker(str, Enum):
    """話者識別"""
    USER = "user"           # XRデバイス装着者
    PARTNER = "partner"     # 会話相手


class EmotionContext(BaseModel):
    """発話時の感情コンテキスト"""
    primary_emotion: str                    # 主要な感情 ("happy", "confused", etc.)
    emotion_scores: dict[str, float]        # 全感情スコア


class Utterance(BaseModel):
    """単一の発話"""
    speaker: Speaker                        # 話者
    text: str                               # 発話内容
    timestamp: datetime                     # タイムスタンプ
    emotion_context: EmotionContext | None = None  # 感情コンテキスト（オプション）
```

### 3. `dto/emotion.py` - 感情解釈

```python
from pydantic import BaseModel


class EmotionInterpretation(BaseModel):
    """感情スコアの解釈結果"""
    primary_emotion: str        # 主要な感情 ("happy", "sad", "confused", etc.)
    intensity: str              # 強度 ("low", "medium", "high")
    description: str            # 自然言語での説明（例: "相手は困惑しているようです"）
    suggestion: str | None = None  # 行動提案（例: "説明を補足すると良いかもしれません"）


class EmotionChange(BaseModel):
    """感情の変化検出結果"""
    from_emotion: str           # 変化前の感情
    to_emotion: str             # 変化後の感情
    description: str            # 変化の説明（例: "相手の表情が曇りました"）
```

### 4. `dto/llm.py` - LLM応答

```python
from pydantic import BaseModel


class ResponseSuggestion(BaseModel):
    """応答候補（1パターン）"""
    text: str           # 応答文
    tone: str           # トーン ("formal", "casual", "empathetic")
    intent: str         # 意図（例: "話題を深める", "共感を示す", "質問する"）


class LLMResponseResult(BaseModel):
    """LLM推論結果"""
    responses: list[ResponseSuggestion]  # 応答候補（3パターン）
    situation_analysis: str              # 状況分析（例: "相手は説明を求めています"）
```

### 5. `dto/processing.py` - 統合結果

```python
from datetime import datetime
from pydantic import BaseModel

from app.dto.audio import TranscriptionResult
from app.dto.emotion import EmotionInterpretation
from app.dto.llm import ResponseSuggestion


class AnalysisRequest(BaseModel):
    """クライアントからの解析リクエスト（WebSocket）"""
    type: str = "ANALYSIS_REQUEST"       # メッセージタイプ
    session_id: str                      # セッションID
    timestamp: datetime                  # クライアントタイムスタンプ
    emotion_scores: dict[str, float]     # 感情スコア（Kotlin側で算出）
    audio_data: str | None = None        # 音声データ（Base64エンコード）
    audio_format: str | None = None      # 音声フォーマット


class AnalysisResponse(BaseModel):
    """解析結果レスポンス（WebSocket）"""
    type: str = "ANALYSIS_RESPONSE"      # メッセージタイプ
    timestamp: datetime                  # サーバータイムスタンプ

    # 感情解釈
    emotion: EmotionInterpretation

    # STT結果（音声があった場合）
    transcription: TranscriptionResult | None = None

    # 応答候補3パターン
    suggestions: list[ResponseSuggestion]

    # 状況分析
    situation_analysis: str

    # 処理時間
    processing_time_ms: int
```

## 感情スコアのキー定義

Kotlin側から送信される `emotion_scores` のキーは以下を想定：

| キー | 説明 | 値域 |
|---|---|---|
| `happy` | 喜び | 0.0 - 1.0 |
| `sad` | 悲しみ | 0.0 - 1.0 |
| `angry` | 怒り | 0.0 - 1.0 |
| `surprised` | 驚き | 0.0 - 1.0 |
| `confused` | 困惑 | 0.0 - 1.0 |
| `neutral` | 中立 | 0.0 - 1.0 |
| `fearful` | 恐怖 | 0.0 - 1.0 |
| `disgusted` | 嫌悪 | 0.0 - 1.0 |

※ 合計が1.0になる必要はない（各感情は独立したスコア）

## 完了条件

- [ ] 全DTOファイルが作成されている
- [ ] 型ヒントが正しく設定されている
- [ ] `mypy`でエラーがない
- [ ] `ruff`でエラーがない
- [ ] 各DTOにdocstringが記載されている

## 関連Issue

- 親Issue: サーバーサイドMVP実装（Epic）
