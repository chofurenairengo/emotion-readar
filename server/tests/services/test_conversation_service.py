"""ConversationService単体テスト."""

import threading
from datetime import datetime, timezone

from app.dto.conversation import EmotionContext, Speaker
from app.services.conversation_service import ConversationService


class TestAddAndGetUtterance:
    """発話の追加と取得テスト."""

    def test_add_and_get_utterance(self) -> None:
        """発話の追加と取得が正しく動作する."""
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
        assert history[0].text == "こんにちは"
        assert history[1].speaker == Speaker.PARTNER
        assert history[1].emotion_context is not None
        assert history[1].emotion_context.primary_emotion == "happy"

    def test_add_utterance_with_timestamp(self) -> None:
        """指定したタイムスタンプで発話が追加される."""
        service = ConversationService()
        session_id = "test-session-timestamp"
        custom_time = datetime(2024, 1, 15, 10, 30, 0, tzinfo=timezone.utc)

        utterance = service.add_utterance(
            session_id=session_id,
            speaker=Speaker.USER,
            text="テスト発話",
            timestamp=custom_time,
        )

        assert utterance.timestamp == custom_time

    def test_add_utterance_auto_timestamp(self) -> None:
        """タイムスタンプ省略時は現在時刻が設定される."""
        service = ConversationService()
        session_id = "test-session-auto-ts"
        before = datetime.now(timezone.utc)

        utterance = service.add_utterance(
            session_id=session_id,
            speaker=Speaker.USER,
            text="テスト発話",
        )

        after = datetime.now(timezone.utc)
        assert before <= utterance.timestamp <= after


class TestMaxHistoryLimit:
    """履歴上限テスト."""

    def test_max_history_limit(self) -> None:
        """履歴上限を超えた場合、古い発話が削除される."""
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
        assert history[4].text == "発話9"

    def test_default_max_history(self) -> None:
        """デフォルトの最大履歴数は100."""
        service = ConversationService()
        assert service._max_history == 100


class TestClearSession:
    """セッションクリアテスト."""

    def test_clear_session(self) -> None:
        """セッションの履歴がクリアされる."""
        service = ConversationService()
        session_id = "test-session-3"

        service.add_utterance(session_id, Speaker.USER, "テスト")
        service.clear(session_id)

        history = service.get_recent_context(session_id)
        assert len(history) == 0

    def test_clear_nonexistent_session(self) -> None:
        """存在しないセッションのクリアはエラーにならない."""
        service = ConversationService()
        service.clear("nonexistent-session")  # 例外が発生しない


class TestGetLastUtterance:
    """最後の発話取得テスト."""

    def test_get_last_utterance(self) -> None:
        """最後の発話を取得できる."""
        service = ConversationService()
        session_id = "test-session-last"

        service.add_utterance(session_id, Speaker.USER, "発話1")
        service.add_utterance(session_id, Speaker.PARTNER, "発話2")
        service.add_utterance(session_id, Speaker.USER, "発話3")

        last = service.get_last_utterance(session_id)
        assert last is not None
        assert last.text == "発話3"
        assert last.speaker == Speaker.USER

    def test_get_last_utterance_by_speaker(self) -> None:
        """特定の話者の最後の発話を取得できる."""
        service = ConversationService()
        session_id = "test-session-speaker"

        service.add_utterance(session_id, Speaker.USER, "USER発話1")
        service.add_utterance(session_id, Speaker.PARTNER, "PARTNER発話1")
        service.add_utterance(session_id, Speaker.USER, "USER発話2")

        last_partner = service.get_last_utterance(session_id, speaker=Speaker.PARTNER)
        assert last_partner is not None
        assert last_partner.text == "PARTNER発話1"

        last_user = service.get_last_utterance(session_id, speaker=Speaker.USER)
        assert last_user is not None
        assert last_user.text == "USER発話2"

    def test_get_last_utterance_empty_session(self) -> None:
        """空のセッションではNoneを返す."""
        service = ConversationService()
        assert service.get_last_utterance("empty-session") is None

    def test_get_last_utterance_no_matching_speaker(self) -> None:
        """該当する話者がいない場合はNoneを返す."""
        service = ConversationService()
        session_id = "test-session-no-match"

        service.add_utterance(session_id, Speaker.USER, "USER発話のみ")

        result = service.get_last_utterance(session_id, speaker=Speaker.PARTNER)
        assert result is None


class TestGetRecentContext:
    """直近の会話履歴取得テスト."""

    def test_get_recent_context_default(self) -> None:
        """デフォルトで最大10件を返す."""
        service = ConversationService()
        session_id = "test-session-recent"

        for i in range(15):
            service.add_utterance(session_id, Speaker.USER, f"発話{i}")

        history = service.get_recent_context(session_id)
        assert len(history) == 10
        assert history[0].text == "発話5"
        assert history[9].text == "発話14"

    def test_get_recent_context_custom_max(self) -> None:
        """指定した件数を返す."""
        service = ConversationService()
        session_id = "test-session-custom"

        for i in range(10):
            service.add_utterance(session_id, Speaker.USER, f"発話{i}")

        history = service.get_recent_context(session_id, max_turns=3)
        assert len(history) == 3
        assert history[0].text == "発話7"

    def test_get_recent_context_empty_session(self) -> None:
        """空のセッションでは空リストを返す."""
        service = ConversationService()
        history = service.get_recent_context("nonexistent-session")
        assert history == []


class TestConversationSummary:
    """会話要約テスト."""

    def test_conversation_summary_basic(self) -> None:
        """基本的な会話要約が生成される."""
        service = ConversationService()
        session_id = "test-session-summary"

        service.add_utterance(session_id, Speaker.USER, "こんにちは")
        service.add_utterance(session_id, Speaker.PARTNER, "こんにちは！")

        summary = service.get_conversation_summary(session_id)
        assert "=== 会話履歴 ===" in summary
        assert "[USER] こんにちは" in summary
        assert "[PARTNER] こんにちは！" in summary

    def test_conversation_summary_with_emotion(self) -> None:
        """感情コンテキスト付きの会話要約."""
        service = ConversationService()
        session_id = "test-session-emotion"

        service.add_utterance(session_id, Speaker.USER, "調子はどう？")
        service.add_utterance(
            session_id,
            Speaker.PARTNER,
            "困っています",
            emotion_context=EmotionContext(
                primary_emotion="困惑",
                emotion_scores={"困惑": 0.7, "neutral": 0.3},
            ),
        )

        summary = service.get_conversation_summary(session_id)
        assert "[PARTNER] (困惑) 困っています" in summary

    def test_conversation_summary_empty(self) -> None:
        """空のセッションでは「履歴なし」を返す."""
        service = ConversationService()
        summary = service.get_conversation_summary("empty-session")
        assert "（履歴なし）" in summary


class TestThreadSafety:
    """スレッドセーフティテスト."""

    def test_concurrent_add_utterance(self) -> None:
        """複数スレッドからの同時追加でデータ破損が起きない."""
        service = ConversationService(max_history_per_session=1000)
        session_id = "test-concurrent"
        num_threads = 10
        utterances_per_thread = 100

        def add_utterances(thread_id: int) -> None:
            for i in range(utterances_per_thread):
                service.add_utterance(
                    session_id,
                    Speaker.USER,
                    f"Thread{thread_id}-Utterance{i}",
                )

        threads = [
            threading.Thread(target=add_utterances, args=(i,))
            for i in range(num_threads)
        ]

        for t in threads:
            t.start()
        for t in threads:
            t.join()

        history = service.get_recent_context(session_id, max_turns=2000)
        assert len(history) == num_threads * utterances_per_thread

    def test_concurrent_read_write(self) -> None:
        """読み書きの同時アクセスでデータ破損が起きない."""
        service = ConversationService()
        session_id = "test-rw-concurrent"
        errors: list[Exception] = []

        def writer() -> None:
            try:
                for i in range(50):
                    service.add_utterance(session_id, Speaker.USER, f"Write{i}")
            except Exception as e:
                errors.append(e)

        def reader() -> None:
            try:
                for _ in range(50):
                    service.get_recent_context(session_id)
                    service.get_conversation_summary(session_id)
            except Exception as e:
                errors.append(e)

        threads = [
            threading.Thread(target=writer),
            threading.Thread(target=reader),
            threading.Thread(target=reader),
        ]

        for t in threads:
            t.start()
        for t in threads:
            t.join()

        assert len(errors) == 0
