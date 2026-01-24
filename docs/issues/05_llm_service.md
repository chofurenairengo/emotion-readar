# LLMサービスの実装

## 概要

Gemini APIを使用して、会話コンテキストと感情情報から応答候補3パターンを生成するサービスを実装する。

## 期待する仕様

### ファイル

```
server/app/services/llm_service.py
```

### インターフェース

```python
from app.dto.conversation import Utterance
from app.dto.emotion import EmotionInterpretation
from app.dto.llm import LLMResponseResult, ResponseSuggestion


class LLMService:
    """LLM推論サービス（Gemini API）"""

    def __init__(self, api_key: str, model: str = "gemini-1.5-flash"):
        """
        初期化

        Args:
            api_key: Gemini APIキー
            model: 使用するモデル名
        """
        pass

    async def generate_responses(
        self,
        conversation_context: list[Utterance],
        emotion_interpretation: EmotionInterpretation,
        partner_last_utterance: str,
    ) -> LLMResponseResult:
        """
        会話コンテキストと感情から応答候補を生成

        Args:
            conversation_context: 直近の会話履歴
            emotion_interpretation: 相手の感情解釈
            partner_last_utterance: 相手の最後の発話

        Returns:
            LLMResponseResult:
                responses: 3パターンの応答候補
                situation_analysis: 状況分析
        """
        pass

    def _build_prompt(
        self,
        context: list[Utterance],
        emotion: EmotionInterpretation,
        last_utterance: str,
    ) -> str:
        """
        LLMへのプロンプトを構築

        Args:
            context: 会話履歴
            emotion: 感情解釈
            last_utterance: 相手の最後の発話

        Returns:
            構築されたプロンプト文字列
        """
        pass

    def _parse_response(
        self,
        raw_response: str,
    ) -> LLMResponseResult:
        """
        LLMのレスポンスをパース

        Args:
            raw_response: LLMからの生レスポンス

        Returns:
            パースされたLLMResponseResult
        """
        pass
```

### プロンプトテンプレート

```python
SYSTEM_PROMPT = """あなたは対面コミュニケーションを支援するアシスタントです。
ユーザー（XRデバイス装着者）が会話相手と円滑にコミュニケーションできるよう、
適切な応答候補を提案してください。

## 入力情報
- 会話履歴
- 相手の現在の感情状態
- 相手の最後の発話

## 出力形式
以下のJSON形式で回答してください:

{
    "situation_analysis": "現在の状況の分析（1-2文）",
    "responses": [
        {
            "text": "応答文1",
            "tone": "formal",
            "intent": "この応答の意図"
        },
        {
            "text": "応答文2",
            "tone": "casual",
            "intent": "この応答の意図"
        },
        {
            "text": "応答文3",
            "tone": "empathetic",
            "intent": "この応答の意図"
        }
    ]
}

## 注意事項
- 応答は必ず3パターン提案してください
- tone は "formal"（丁寧）, "casual"（カジュアル）, "empathetic"（共感的）のいずれか
- 相手の感情状態を考慮した応答を生成してください
- 日本語で応答してください
"""

USER_PROMPT_TEMPLATE = """## 会話履歴
{conversation_history}

## 相手の感情状態
- 主要な感情: {primary_emotion}
- 強度: {intensity}
- 解釈: {description}

## 相手の最後の発話
「{last_utterance}」

上記の情報をもとに、ユーザーが返すべき応答候補を3パターン提案してください。
"""
```

### 応答のトーン定義

| トーン | 説明 | 使用場面 |
|---|---|---|
| `formal` | 丁寧・フォーマル | ビジネス、初対面 |
| `casual` | カジュアル・親しみやすい | 友人、リラックスした場面 |
| `empathetic` | 共感的・寄り添い | 相手が困っている、悲しんでいる |

### 応答の意図（intent）例

- 「話題を深める」
- 「共感を示す」
- 「質問する」
- 「話題を変える」
- 「確認する」
- 「提案する」
- 「励ます」

### エラーハンドリング

```python
# app/core/exceptions.py に追加

class LLMError(Exception):
    """LLM推論エラー"""
    pass

class LLMRateLimitError(LLMError):
    """レートリミットエラー"""
    pass

class LLMResponseParseError(LLMError):
    """レスポンスパースエラー"""
    pass
```

### リトライ設定

```python
# レートリミット時のリトライ設定
RETRY_CONFIG = {
    "max_retries": 3,
    "initial_delay": 1.0,  # 秒
    "exponential_base": 2,
    "max_delay": 10.0,
}
```

### 依存関係追加（`pyproject.toml`）

```toml
dependencies = [
    "google-generativeai>=0.5.0",
]
```

### 環境変数

```
GEMINI_API_KEY=your-api-key-here
```

## レスポンス例

### 入力

```python
conversation_context = [
    Utterance(speaker=Speaker.USER, text="明日の会議の件で相談があります"),
    Utterance(speaker=Speaker.PARTNER, text="はい、どのような相談でしょうか？"),
]
emotion_interpretation = EmotionInterpretation(
    primary_emotion="confused",
    intensity="medium",
    description="相手は困惑しているようです",
    suggestion="説明を補足すると良いかもしれません",
)
partner_last_utterance = "はい、どのような相談でしょうか？"
```

### 出力

```python
LLMResponseResult(
    situation_analysis="相手は会議の件について詳細を知りたがっていますが、少し困惑しているようです。具体的な内容を伝えると良いでしょう。",
    responses=[
        ResponseSuggestion(
            text="明日の会議の開始時間を30分後ろ倒しにできないかと思っているのですが、ご都合はいかがでしょうか？",
            tone="formal",
            intent="具体的な提案をする",
        ),
        ResponseSuggestion(
            text="実は時間を変更したくて。午後2時からに変えられないかな？",
            tone="casual",
            intent="カジュアルに提案する",
        ),
        ResponseSuggestion(
            text="すみません、急で申し訳ないのですが、明日の会議の時間調整をお願いできませんか？ご負担でなければ...",
            tone="empathetic",
            intent="相手に配慮しながら提案する",
        ),
    ],
)
```

## 完了条件

- [ ] `LLMService`クラスが実装されている
- [ ] Gemini APIを呼び出して応答を取得できる
- [ ] 3パターンの応答が生成される
- [ ] レスポンスが正しくパースされる
- [ ] エラーハンドリングが実装されている
- [ ] リトライ機能が実装されている
- [ ] 単体テストが作成されている（モック使用）
- [ ] `mypy`でエラーがない

## テストケース

```python
import pytest
from unittest.mock import AsyncMock, patch
from app.services.llm_service import LLMService
from app.dto.conversation import Utterance, Speaker
from app.dto.emotion import EmotionInterpretation


@pytest.mark.asyncio
async def test_generate_responses():
    """応答生成テスト（モック使用）"""
    mock_response = """
    {
        "situation_analysis": "テスト分析",
        "responses": [
            {"text": "応答1", "tone": "formal", "intent": "テスト"},
            {"text": "応答2", "tone": "casual", "intent": "テスト"},
            {"text": "応答3", "tone": "empathetic", "intent": "テスト"}
        ]
    }
    """

    with patch.object(LLMService, '_call_api', new_callable=AsyncMock) as mock_api:
        mock_api.return_value = mock_response

        service = LLMService(api_key="test-key")
        result = await service.generate_responses(
            conversation_context=[
                Utterance(speaker=Speaker.USER, text="テスト", timestamp=...),
            ],
            emotion_interpretation=EmotionInterpretation(
                primary_emotion="neutral",
                intensity="medium",
                description="テスト",
            ),
            partner_last_utterance="テスト発話",
        )

        assert len(result.responses) == 3
        assert result.situation_analysis == "テスト分析"


def test_build_prompt():
    """プロンプト構築テスト"""
    service = LLMService(api_key="test-key")
    prompt = service._build_prompt(
        context=[...],
        emotion=EmotionInterpretation(...),
        last_utterance="こんにちは",
    )

    assert "こんにちは" in prompt
    assert "会話履歴" in prompt
```

## 関連Issue

- 親Issue: サーバーサイドMVP実装（Epic）
- 依存: #1 DTO定義
