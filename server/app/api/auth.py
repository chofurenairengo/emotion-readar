"""JWT検証 FastAPI Dependency。

Authorization: Bearer <token> ヘッダーからFirebase ID Tokenを検証し、
ユーザー情報を返す。

開発用バイパス:
    DEV_AUTH_BYPASS=true を設定すると認証をスキップし、
    テスト用のユーザー情報を返す（本番環境では絶対に使用しないこと）
"""

from __future__ import annotations

import logging
import os
from typing import Any

from fastapi import HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.infra.firebase import verify_id_token

logger = logging.getLogger(__name__)

_bearer_scheme = HTTPBearer(auto_error=False)

# 開発用認証バイパス（本番では絶対にtrueにしないこと）
_DEV_AUTH_BYPASS = os.environ.get("DEV_AUTH_BYPASS", "").lower() == "true"

_DEV_USER: dict[str, Any] = {
    "uid": "dev-test-user",
    "email": "dev@test.local",
}

if _DEV_AUTH_BYPASS:
    logger.warning("⚠️ DEV_AUTH_BYPASS is enabled - authentication is disabled!")


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
