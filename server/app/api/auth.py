"""JWT検証 FastAPI Dependency。

Authorization: Bearer <token> ヘッダーからFirebase ID Tokenを検証し、
ユーザー情報を返す。

開発用バイパス:
    DEV_AUTH_BYPASS=true を設定すると認証をスキップし、
    テスト用のユーザー情報を返す（本番環境では絶対に使用しないこと）
"""

from __future__ import annotations

import logging
import re
from typing import Any

from fastapi import Depends, HTTPException, Request, Response, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import get_settings
from app.infra.firebase import verify_id_token
from app.services.rate_limiter import get_rate_limiter

logger = logging.getLogger(__name__)

_bearer_scheme = HTTPBearer(auto_error=False)

# 開発用認証バイパス設定
_settings = get_settings()
_DEV_AUTH_BYPASS: bool = _settings.DEV_AUTH_BYPASS
_DEV_USER: dict[str, Any] = {
    "uid": "dev-user-001",
    "email": "dev@example.com",
}

# UUID正規表現パターン
_UUID_PATTERN = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    re.IGNORECASE,
)


async def get_current_user(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = None,
) -> dict[str, Any]:
    """認証済みユーザー情報を取得する FastAPI Dependency。

    Authorization: Bearer <token> ヘッダーを検証し、
    デコード済みクレームを返す。
    検証済み情報は request.state.user にも格納される。

    開発用バイパス:
        DEV_AUTH_BYPASS=true の場合、認証をスキップしてテストユーザーを返す。

    Raises:
        HTTPException(401): トークンが無い、無効、または期限切れの場合
    """
    # 開発用バイパス
    if _DEV_AUTH_BYPASS:
        request.state.user = _DEV_USER
        return _DEV_USER

    if credentials is None:
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Missing authentication token",
                headers={"WWW-Authenticate": "Bearer"},
            )
        token = auth_header[len("Bearer "):].strip()
    else:
        token = credentials.credentials

    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing authentication token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        decoded = verify_id_token(token)
    except Exception as exc:
        logger.warning("Token verification failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

    user_info: dict[str, Any] = {
        "uid": decoded.get("uid", ""),
        "email": decoded.get("email"),
    }
    request.state.user = user_info
    return user_info


def verify_websocket_token(token: str) -> dict[str, Any]:
    """WebSocket接続用のトークン検証.

    Args:
        token: Firebase ID Token

    Returns:
        dict: ユーザー情報 {"uid": str, "email": str | None}

    開発用バイパス:
        DEV_AUTH_BYPASS=true の場合、認証をスキップしてテストユーザーを返す。

    Raises:
        ValueError: トークンが無効または期限切れの場合
    """
    # 開発用バイパス
    if _DEV_AUTH_BYPASS:
        return _DEV_USER

    if not token:
        raise ValueError("Missing authentication token")

    try:
        decoded = verify_id_token(token)
    except Exception as exc:
        logger.warning("WebSocket token verification failed: %s", exc)
        raise ValueError("Invalid or expired token") from exc

    return {
        "uid": decoded.get("uid", ""),
        "email": decoded.get("email"),
    }


def _normalize_path(path: str) -> str:
    """パスパラメータ（UUID）を * に置換してパスを正規化。"""
    parts = path.split("/")
    return "/".join(
        "*" if _UUID_PATTERN.match(part) else part for part in parts
    )


def _get_rate_limit_key(user_id: str, path: str) -> str:
    """レート制限用のキーを生成。"""
    return f"ratelimit:{user_id}:{_normalize_path(path)}"


def _matches_pattern(pattern: str, path: str) -> bool:
    """パターン（*を含む）とパスがマッチするか判定。"""
    if "*" not in pattern:
        return pattern == path
    # パターンを正規表現に変換（* を [^/]+ に置換）
    regex_pattern = "^" + pattern.replace("*", "[^/]+") + "$"
    return bool(re.match(regex_pattern, path))


def _get_limit_for_path(path: str) -> int:
    """パスに対応するレート制限値を取得。"""
    settings = get_settings()
    rate_limits = settings.rate_limits
    normalized_path = _normalize_path(path)

    # 完全一致を優先
    if normalized_path in rate_limits:
        return rate_limits[normalized_path]

    # パターンマッチング（* を含むキーに対応）
    for pattern, limit in rate_limits.items():
        if pattern == "default":
            continue
        if _matches_pattern(pattern, normalized_path):
            return limit

    return rate_limits.get("default", 100)


async def check_rate_limit(
    request: Request,
    response: Response,
    current_user: dict[str, Any] = Depends(get_current_user),
) -> dict[str, Any]:
    """レート制限をチェックするDependency。

    認証済みユーザーのリクエストを制限し、
    X-RateLimit-* ヘッダーを付与する。

    Raises:
        HTTPException(429): レート制限超過時
    """
    settings = get_settings()
    rate_limiter = get_rate_limiter()

    user_id = current_user["uid"]
    path = request.url.path
    limit = _get_limit_for_path(path)
    window_seconds = settings.RATE_LIMIT_WINDOW_SECONDS

    key = _get_rate_limit_key(user_id, path)
    result = rate_limiter.check_and_increment(key, limit, window_seconds)

    # レスポンスヘッダーを追加
    response.headers["X-RateLimit-Limit"] = str(result.limit)
    response.headers["X-RateLimit-Remaining"] = str(result.remaining)
    response.headers["X-RateLimit-Reset"] = str(result.reset_at)

    if not result.allowed:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Rate limit exceeded. Try again later.",
            headers={
                "X-RateLimit-Limit": str(result.limit),
                "X-RateLimit-Remaining": str(result.remaining),
                "X-RateLimit-Reset": str(result.reset_at),
                "Retry-After": str(window_seconds),
            },
        )

    return current_user
