# 応答生成サービス（統合）の実装

## 概要

STT、会話履歴、感情解釈、LLMの各サービスを統合し、クライアントからのリクエストを処理して最終レスポンスを生成するサービスを実装する。

## 期待する仕様

### ファイル

```
server/app/services/response_generator.py
```

### インターフェース

```python
import time
from datetime import datetime, timezone

from app.dto.audio import AudioFormat, TranscriptionResult
from app.dto.conversation import Speaker, EmotionContext
from app.dto.emotion import EmotionInterpretation
from app.dto.llm import ResponseSuggestion
from app.dto.processing import AnalysisResponse
from app.services.stt_service import STTService
from app.services.conversation_service import ConversationService
from app.services.emotion_interpreter import EmotionInterpreterService
from app.services.llm_service import LLMService


class ResponseGeneratorService:
    """各サービスを統合して応答を生成"""

    def __init__(
        self,
        stt_service: STTService,
        conversation_service: ConversationService,
        emotion_interpreter: EmotionInterpreterService,
        llm_service: LLMService,
    ):
        """
        初期化

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
        メイン処理パイプライン

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
        pass

    async def _transcribe_audio(
        self,
        audio_data: bytes,
        audio_format: AudioFormat,
    ) -> TranscriptionResult | None:
        """
        音声をテキストに変換（内部メソッド）

        Args:
            audio_data: 音声データ
            audio_format: フォーマット

        Returns:
            TranscriptionResult or None（失敗時）
        """
        pass

    def _update_conversation(
        self,
        session_id: str,
        text: str,
        emotion_scores: dict[str, float],
    ) -> None:
        """
        会話履歴を更新（内部メソッド）

        Args:
            session_id: セッションID
            text: 発話テキスト
            emotion_scores: 感情スコア
        """
        pass
```

### 処理フロー詳細

```
process() メソッドの処理フロー:

┌─────────────────────────────────────────────────────────┐
│ 1. 処理開始時刻を記録                                    │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│ 2. 音声データがある場合                                  │
│    └─ STTサービスでテキスト変換                          │
│    └─ 失敗時はNone、処理は継続                           │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│ 3. 会話履歴に追加                                        │
│    └─ speaker = PARTNER（会話相手の発話として記録）      │
│    └─ 感情コンテキストも付与                             │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│ 4. 感情スコアを解釈                                      │
│    └─ EmotionInterpreterService.interpret()              │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│ 5. LLM推論                                               │
│    └─ 会話履歴 + 感情解釈 + 最後の発話 を入力            │
│    └─ 3パターンの応答候補を生成                          │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│ 6. 結果統合                                              │
│    └─ AnalysisResponse を構築                            │
│    └─ 処理時間を計算                                     │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
                         [レスポンス返却]
```

### 実装例

```python
async def process(
    self,
    session_id: str,
    emotion_scores: dict[str, float],
    audio_data: bytes | None = None,
    audio_format: AudioFormat | None = None,
) -> AnalysisResponse:
    start_time = time.perf_counter()

    # 1. STT処理
    transcription: TranscriptionResult | None = None
    partner_utterance: str = ""

    if audio_data and audio_format:
        transcription = await self._transcribe_audio(audio_data, audio_format)
        if transcription:
            partner_utterance = transcription.text

    # 2. 会話履歴に追加（テキストがある場合）
    if partner_utterance:
        self._update_conversation(session_id, partner_utterance, emotion_scores)

    # 3. 感情解釈
    emotion_interpretation = self._emotion.interpret(emotion_scores)

    # 4. LLM推論
    conversation_context = self._conversation.get_recent_context(
        session_id, max_turns=10
    )

    llm_result = await self._llm.generate_responses(
        conversation_context=conversation_context,
        emotion_interpretation=emotion_interpretation,
        partner_last_utterance=partner_utterance or "(発話なし)",
    )

    # 5. 結果統合
    processing_time_ms = int((time.perf_counter() - start_time) * 1000)

    return AnalysisResponse(
        type="ANALYSIS_RESPONSE",
        timestamp=datetime.now(timezone.utc),
        emotion=emotion_interpretation,
        transcription=transcription,
        suggestions=llm_result.responses,
        situation_analysis=llm_result.situation_analysis,
        processing_time_ms=processing_time_ms,
    )
```

### エラーハンドリング方針

| エラー種別 | 対応 |
|---|---|
| STT失敗 | ログ出力、処理継続（transcription=None） |
| 会話履歴更新失敗 | ログ出力、処理継続 |
| 感情解釈失敗 | デフォルト値（neutral）で継続 |
| LLM失敗 | 例外を上位に伝播（WebSocketハンドラでエラーレスポンス） |

### ロギング

```python
import logging

logger = logging.getLogger(__name__)

# 処理開始
logger.info(f"Processing request for session {session_id}")

# STT結果
logger.debug(f"STT result: {transcription}")

# LLM呼び出し
logger.info(f"Calling LLM with {len(conversation_context)} context turns")

# 処理完了
logger.info(f"Processing completed in {processing_time_ms}ms")
```

## 依存性注入設定

`app/api/dependencies.py` に追加:

```python
from app.services.stt_service import STTService
from app.services.conversation_service import ConversationService
from app.services.emotion_interpreter import EmotionInterpreterService
from app.services.llm_service import LLMService
from app.services.response_generator import ResponseGeneratorService

# シングルトンインスタンス
_stt_service: STTService | None = None
_conversation_service: ConversationService | None = None
_emotion_interpreter: EmotionInterpreterService | None = None
_llm_service: LLMService | None = None
_response_generator: ResponseGeneratorService | None = None


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
```

## 完了条件

- [ ] `ResponseGeneratorService`クラスが実装されている
- [ ] 全サービスが正しく統合されている
- [ ] 音声ありの場合でも音声なしの場合でも処理できる
- [ ] 処理時間が計測されている
- [ ] エラーハンドリングが実装されている
- [ ] ログ出力が実装されている
- [ ] 単体テストが作成されている
- [ ] 統合テストが作成されている
- [ ] `mypy`でエラーがない

## テストケース

```python
import pytest
from unittest.mock import AsyncMock, MagicMock
from app.services.response_generator import ResponseGeneratorService
from app.dto.audio import AudioFormat, TranscriptionResult
from app.dto.emotion import EmotionInterpretation
from app.dto.llm import LLMResponseResult, ResponseSuggestion


@pytest.fixture
def mock_services():
    """モックサービスのフィクスチャ"""
    stt = MagicMock()
    stt.transcribe = AsyncMock(return_value=TranscriptionResult(
        text="こんにちは",
        confidence=0.95,
        language="ja",
        duration_ms=1000,
    ))

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
    llm.generate_responses = AsyncMock(return_value=LLMResponseResult(
        responses=[
            ResponseSuggestion(text="応答1", tone="formal", intent="テスト"),
            ResponseSuggestion(text="応答2", tone="casual", intent="テスト"),
            ResponseSuggestion(text="応答3", tone="empathetic", intent="テスト"),
        ],
        situation_analysis="テスト分析",
    ))

    return stt, conversation, emotion, llm


@pytest.mark.asyncio
async def test_process_with_audio(mock_services):
    """音声ありの処理テスト"""
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
    assert len(result.suggestions) == 3
    assert result.processing_time_ms > 0


@pytest.mark.asyncio
async def test_process_without_audio(mock_services):
    """音声なしの処理テスト"""
    stt, conversation, emotion, llm = mock_services
    service = ResponseGeneratorService(stt, conversation, emotion, llm)

    result = await service.process(
        session_id="test-session",
        emotion_scores={"confused": 0.7},
    )

    assert result.transcription is None
    assert len(result.suggestions) == 3
    stt.transcribe.assert_not_called()
```

## 関連Issue

- 親Issue: サーバーサイドMVP実装（Epic）
- 依存: #2 STTサービス, #3 会話履歴サービス, #4 感情解釈サービス, #5 LLMサービス
