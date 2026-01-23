# 会話履歴管理サービスの実装

## 概要

セッション内の会話コンテキストを管理し、LLM推論に必要な履歴を提供するサービスを実装する。

## 期待する仕様

### ファイル

```
server/app/services/conversation_service.py
```

### インターフェース

```python
from datetime import datetime
from app.dto.conversation import Speaker, Utterance, EmotionContext


class ConversationService:
    """会話履歴管理サービス"""

    def __init__(self, max_history_per_session: int = 100):
        """
        初期化

        Args:
            max_history_per_session: セッションあたりの最大履歴数
        """
        self._histories: dict[str, list[Utterance]] = {}
        self._max_history = max_history_per_session

    def add_utterance(
        self,
        session_id: str,
        speaker: Speaker,
        text: str,
        emotion_context: EmotionContext | None = None,
        timestamp: datetime | None = None,
    ) -> Utterance:
        """
        発話を履歴に追加

        Args:
            session_id: セッションID
            speaker: 話者 (USER or PARTNER)
            text: 発話内容
            emotion_context: 発話時の感情コンテキスト（オプション）
            timestamp: タイムスタンプ（省略時は現在時刻）

        Returns:
            追加されたUtteranceオブジェクト
        """
        pass

    def get_recent_context(
        self,
        session_id: str,
        max_turns: int = 10,
    ) -> list[Utterance]:
        """
        直近の会話履歴を取得

        Args:
            session_id: セッションID
            max_turns: 取得する最大ターン数

        Returns:
            直近の発話リスト（古い順）
        """
        pass

    def get_last_utterance(
        self,
        session_id: str,
        speaker: Speaker | None = None,
    ) -> Utterance | None:
        """
        最後の発話を取得

        Args:
            session_id: セッションID
            speaker: 特定の話者でフィルタ（オプション）

        Returns:
            最後の発話、存在しない場合はNone
        """
        pass

    def clear(self, session_id: str) -> None:
        """
        セッションの履歴をクリア

        Args:
            session_id: セッションID
        """
        pass

    def get_conversation_summary(
        self,
        session_id: str,
    ) -> str:
        """
        会話の要約を生成（LLMプロンプト用）

        Args:
            session_id: セッションID

        Returns:
            会話履歴のテキスト要約
        """
        pass
```

### 内部データ構造

```python
# セッションIDをキーとした辞書でメモリ管理
_histories: dict[str, list[Utterance]] = {}

# スレッドセーフのためのロック
_lock: threading.Lock
```

### 会話要約フォーマット

`get_conversation_summary()` の出力例:

```
=== 会話履歴 ===
[USER] こんにちは、今日の会議の件で相談があります。
[PARTNER] (困惑) はい、どのような相談でしょうか？
[USER] 明日の会議の時間を変更したいのですが...
[PARTNER] (中立) 何時に変更したいですか？
```

### 履歴の上限管理

- セッションあたり最大100件（デフォルト）
- 上限を超えた場合、古い履歴から削除（FIFO）
- 削除時にログを出力

### スレッドセーフティ

- 複数のWebSocket接続から同時アクセスされる可能性
- `threading.Lock` または `asyncio.Lock` を使用

## 完了条件

- [ ] `ConversationService`クラスが実装されている
- [ ] 発話の追加・取得・削除ができる
- [ ] 履歴上限が正しく機能する
- [ ] スレッドセーフに実装されている
- [ ] 単体テストが作成されている
- [ ] `mypy`でエラーがない

## テストケース

```python
import pytest
from datetime import datetime
from app.services.conversation_service import ConversationService
from app.dto.conversation import Speaker, EmotionContext


def test_add_and_get_utterance():
    """発話の追加と取得テスト"""
    service = ConversationService()
    session_id = "test-session-1"

    # 発話を追加
    service.add_utterance(
        session_id=session_id,
        speaker=Speaker.USER,
        text="こんにちは",
    )
    service.add_utterance(
        session_id=session_id,
        speaker=Speaker.PARTNER,
        text="こんにちは、元気ですか？",
        emotion_context=EmotionContext(
            primary_emotion="happy",
            emotion_scores={"happy": 0.8, "neutral": 0.2},
        ),
    )

    # 履歴を取得
    history = service.get_recent_context(session_id, max_turns=10)

    assert len(history) == 2
    assert history[0].speaker == Speaker.USER
    assert history[1].speaker == Speaker.PARTNER
    assert history[1].emotion_context is not None


def test_max_history_limit():
    """履歴上限テスト"""
    service = ConversationService(max_history_per_session=5)
    session_id = "test-session-2"

    # 10件追加
    for i in range(10):
        service.add_utterance(
            session_id=session_id,
            speaker=Speaker.USER,
            text=f"発話{i}",
        )

    # 5件のみ保持されている
    history = service.get_recent_context(session_id, max_turns=100)
    assert len(history) == 5
    assert history[0].text == "発話5"  # 古い5件は削除


def test_clear_session():
    """セッションクリアテスト"""
    service = ConversationService()
    session_id = "test-session-3"

    service.add_utterance(session_id, Speaker.USER, "テスト")
    service.clear(session_id)

    history = service.get_recent_context(session_id)
    assert len(history) == 0
```

## 関連Issue

- 親Issue: サーバーサイドMVP実装（Epic）
- 依存: #1 DTO定義
