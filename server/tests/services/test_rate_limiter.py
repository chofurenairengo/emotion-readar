"""レート制限サービスのユニットテスト。"""

from unittest.mock import patch

from app.services.rate_limiter import InMemoryRateLimiter


class TestInMemoryRateLimiter:
    """InMemoryRateLimiterのテスト"""

    def test_allows_requests_under_limit(self) -> None:
        """制限未満のリクエストは許可される"""
        limiter = InMemoryRateLimiter()
        result = limiter.check_and_increment("user1:/api/test", limit=5, window_seconds=60)

        assert result.allowed is True
        assert result.limit == 5
        assert result.remaining == 4

    def test_blocks_requests_at_limit(self) -> None:
        """制限到達時はリクエストがブロックされる"""
        limiter = InMemoryRateLimiter()

        # 制限まで消費
        for _ in range(5):
            limiter.check_and_increment("user1:/api/test", limit=5, window_seconds=60)

        # 6回目はブロック
        result = limiter.check_and_increment("user1:/api/test", limit=5, window_seconds=60)

        assert result.allowed is False
        assert result.remaining == 0

    def test_different_users_have_separate_limits(self) -> None:
        """異なるユーザーは別々にカウントされる"""
        limiter = InMemoryRateLimiter()

        # user1が制限到達
        for _ in range(5):
            limiter.check_and_increment("user1:/api/test", limit=5, window_seconds=60)

        # user2は別カウント
        result = limiter.check_and_increment("user2:/api/test", limit=5, window_seconds=60)

        assert result.allowed is True

    def test_requests_expire_after_window(self) -> None:
        """ウィンドウ経過後はリクエストがリセットされる"""
        limiter = InMemoryRateLimiter()

        with patch("app.services.rate_limiter.time.time") as mock_time:
            # 時刻1000でリクエスト
            mock_time.return_value = 1000.0
            for _ in range(5):
                limiter.check_and_increment("user1:/api/test", limit=5, window_seconds=60)

            # 時刻1061（61秒後）でリクエスト
            mock_time.return_value = 1061.0
            result = limiter.check_and_increment("user1:/api/test", limit=5, window_seconds=60)

            assert result.allowed is True

    def test_get_current_count(self) -> None:
        """現在のカウントを正しく取得できる"""
        limiter = InMemoryRateLimiter()

        limiter.check_and_increment("user1:/api/test", limit=10, window_seconds=60)
        limiter.check_and_increment("user1:/api/test", limit=10, window_seconds=60)

        count = limiter.get_current_count("user1:/api/test", window_seconds=60)

        assert count == 2

    def test_clear_removes_all_data(self) -> None:
        """clearで全データがクリアされる"""
        limiter = InMemoryRateLimiter()
        limiter.check_and_increment("user1:/api/test", limit=10, window_seconds=60)

        limiter.clear()

        count = limiter.get_current_count("user1:/api/test", window_seconds=60)
        assert count == 0
