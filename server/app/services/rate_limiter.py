"""レート制限サービス。

Sliding Window アルゴリズムでユーザーごとのリクエスト数を制限する。
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from functools import lru_cache
from threading import Lock
from typing import Protocol


@dataclass(frozen=True)
class RateLimitResult:
    """レート制限チェック結果。"""

    allowed: bool
    limit: int
    remaining: int
    reset_at: int  # Unix timestamp


class RateLimiterBackend(Protocol):
    """レート制限バックエンドのインターフェース。"""

    def check_and_increment(
        self,
        key: str,
        limit: int,
        window_seconds: int,
    ) -> RateLimitResult:
        """リクエストをチェックし、許可された場合はカウントを増やす。"""
        ...

    def get_current_count(self, key: str, window_seconds: int) -> int:
        """現在のリクエスト数を取得。"""
        ...


class InMemoryRateLimiter:
    """インメモリのレート制限バックエンド。

    開発・テスト用。本番環境ではRedisやFirestoreに置き換え可能。
    """

    def __init__(self) -> None:
        self._requests: dict[str, list[float]] = {}
        self._lock = Lock()

    def check_and_increment(
        self,
        key: str,
        limit: int,
        window_seconds: int,
    ) -> RateLimitResult:
        """リクエストをチェックし、許可された場合はカウントを増やす。"""
        now = time.time()
        window_start = now - window_seconds
        reset_at = int(now) + window_seconds

        with self._lock:
            # 期限切れのリクエストを削除
            if key in self._requests:
                self._requests[key] = [
                    ts for ts in self._requests[key] if ts > window_start
                ]
            else:
                self._requests[key] = []

            current_count = len(self._requests[key])

            if current_count >= limit:
                return RateLimitResult(
                    allowed=False,
                    limit=limit,
                    remaining=0,
                    reset_at=reset_at,
                )

            # リクエストを記録
            self._requests[key].append(now)

            return RateLimitResult(
                allowed=True,
                limit=limit,
                remaining=limit - current_count - 1,
                reset_at=reset_at,
            )

    def get_current_count(self, key: str, window_seconds: int) -> int:
        """現在のリクエスト数を取得。"""
        now = time.time()
        window_start = now - window_seconds

        with self._lock:
            if key not in self._requests:
                return 0
            return len([ts for ts in self._requests[key] if ts > window_start])

    def clear(self) -> None:
        """全データをクリア（テスト用）。"""
        with self._lock:
            self._requests.clear()


@lru_cache
def get_rate_limiter() -> InMemoryRateLimiter:
    """レート制限サービスのシングルトンを取得。"""
    return InMemoryRateLimiter()
