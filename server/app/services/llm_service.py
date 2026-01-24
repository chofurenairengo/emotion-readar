"""LLM推論サービス（Gemini API）."""

from __future__ import annotations

import asyncio
import json
import logging
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_google_vertexai import ChatVertexAI

from app.core.exceptions import LLMError, LLMRateLimitError, LLMResponseParseError
from app.dto.conversation import Utterance
from app.dto.emotion import EmotionInterpretation
from app.dto.llm import LLMResponseResult, ResponseSuggestion

logger = logging.getLogger(__name__)

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
    "situation_analysis": "現在の状況分析（20〜30文字程度の簡潔な1文）",
    "responses": [
        {
            "text": "応答文1",
            "intent": "この応答の意図"
        },
        {
            "text": "応答文2",
            "intent": "この応答の意図"
        }
    ]
}

## 注意事項
- 応答は必ず2パターン提案してください
- 2つの応答は異なる意図・アプローチである必要があります
- 相手の感情状態を考慮した応答を生成してください
- 日本語で応答してください
- JSON以外の文字列は含めないでください
"""

MAX_RETRIES = 3
INITIAL_DELAY = 1.0
EXPONENTIAL_BASE = 2.0
MAX_DELAY = 10.0


class LLMService:
    """LLM推論サービス（Gemini API）."""

    def __init__(
        self,
        project: str | None = None,
        location: str = "asia-northeast1",
        model: str = "gemini-2.5-flash",
    ) -> None:
        """初期化.

        Args:
            project: GCPプロジェクトID
            location: Vertex AIのリージョン
            model: 使用するモデル名
        """
        self._model = ChatVertexAI(
            model=model,
            project=project,
            location=location,
            temperature=0.7,
        )

    async def generate_responses(
        self,
        conversation_context: list[Utterance],
        emotion_interpretation: EmotionInterpretation,
        partner_last_utterance: str,
    ) -> LLMResponseResult:
        """会話コンテキストと感情から応答候補を生成.

        Args:
            conversation_context: 直近の会話履歴
            emotion_interpretation: 相手の感情解釈
            partner_last_utterance: 相手の最後の発話

        Returns:
            LLMResponseResult: 2パターンの応答候補と状況分析

        Raises:
            LLMError: LLM推論エラー
            LLMRateLimitError: レートリミットエラー
            LLMResponseParseError: レスポンスパースエラー
        """
        prompt = self._build_prompt(
            conversation_context,
            emotion_interpretation,
            partner_last_utterance,
        )

        raw_response = await self._call_api_with_retry(prompt)
        return self._parse_response(raw_response)

    def _build_prompt(
        self,
        context: list[Utterance],
        emotion: EmotionInterpretation,
        last_utterance: str,
    ) -> str:
        """LLMへのプロンプトを構築.

        Args:
            context: 会話履歴
            emotion: 感情解釈
            last_utterance: 相手の最後の発話

        Returns:
            構築されたプロンプト
        """
        conversation_text = "\n".join(f"- {u.speaker.value}: {u.text}" for u in context)

        emotion_text = (
            f"主要な感情: {emotion.primary_emotion}\n"
            f"強度: {emotion.intensity}\n"
            f"説明: {emotion.description}"
        )
        if emotion.suggestion:
            emotion_text += f"\n提案: {emotion.suggestion}"

        return f"""## 会話履歴
{conversation_text}

## 相手の感情状態
{emotion_text}

## 相手の最後の発話
{last_utterance}

上記の情報を踏まえて、ユーザーが相手に返すべき応答候補を2パターン提案してください。"""

    def _parse_response(self, raw_response: str) -> LLMResponseResult:
        """LLMのレスポンスをパース.

        Args:
            raw_response: LLMからの生レスポンス

        Returns:
            パースされたLLMResponseResult

        Raises:
            LLMResponseParseError: パースに失敗した場合
        """
        try:
            cleaned = raw_response.strip()
            if cleaned.startswith("```json"):
                cleaned = cleaned[7:]
            if cleaned.startswith("```"):
                cleaned = cleaned[3:]
            if cleaned.endswith("```"):
                cleaned = cleaned[:-3]
            cleaned = cleaned.strip()

            data: dict[str, Any] = json.loads(cleaned)

            responses = [
                ResponseSuggestion(
                    text=r["text"],
                    intent=r["intent"],
                )
                for r in data["responses"]
            ]

            return LLMResponseResult(
                situation_analysis=data["situation_analysis"],
                responses=responses,
            )
        except json.JSONDecodeError as e:
            logger.error(f"JSON parse error: {e}, response: {raw_response[:200]}")
            raise LLMResponseParseError(f"JSONのパースに失敗しました: {e}") from e
        except KeyError as e:
            logger.error(f"Missing key: {e}, response: {raw_response[:200]}")
            raise LLMResponseParseError(f"必要なキーがありません: {e}") from e
        except ValueError as e:
            logger.error(f"Validation error: {e}")
            raise LLMResponseParseError(f"バリデーションエラー: {e}") from e

    async def _call_api_with_retry(self, prompt: str) -> str:
        """リトライ付きでAPIを呼び出す.

        Args:
            prompt: ユーザープロンプト

        Returns:
            LLMからのレスポンス

        Raises:
            LLMRateLimitError: リトライ上限に達した場合
            LLMError: その他のAPIエラー
        """
        delay = INITIAL_DELAY
        last_exception: Exception | None = None

        for attempt in range(MAX_RETRIES):
            try:
                return await self._call_api(prompt)
            except Exception as e:
                last_exception = e
                error_str = str(e).lower()

                if "rate" in error_str or "429" in error_str or "quota" in error_str:
                    logger.warning(
                        f"Rate limit hit, attempt {attempt + 1}/"
                        f"{MAX_RETRIES}, waiting {delay}s"
                    )
                    await asyncio.sleep(delay)
                    delay = min(delay * EXPONENTIAL_BASE, MAX_DELAY)
                else:
                    logger.error(f"API error: {e}")
                    raise LLMError(f"API呼び出しエラー: {e}") from e

        raise LLMRateLimitError(
            f"リトライ上限に達しました: {last_exception}"
        ) from last_exception

    async def _call_api(self, prompt: str) -> str:
        """APIを呼び出す.

        Args:
            prompt: ユーザープロンプト

        Returns:
            LLMからのレスポンス
        """
        messages = [
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=prompt),
        ]

        response = await self._model.ainvoke(messages)
        return str(response.content)
