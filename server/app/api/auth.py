"""JWT検証 FastAPI Dependency。

Authorization: Bearer <token> ヘッダーからFirebase ID Tokenを検証し、
ユーザー情報を返す。
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.infra.firebase import verify_id_token

logger = logging.getLogger(__name__)

_bearer_scheme = HTTPBearer(auto_error=False)


async def get_current_user(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = None,
) -> dict[str, Any]:
    """認証済みユーザー情報を取得する FastAPI Dependency。

    Authorization: Bearer <token> ヘッダーを検証し、
    デコード済みクレームを返す。
    検証済み情報は request.state.user にも格納される。

    Raises:
        HTTPException(401): トークンが無い、無効、または期限切れの場合
    """
    if credentials is None:
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Missing authentication token",
                headers={"WWW-Authenticate": "Bearer"},
            )
        token = auth_header[len("Bearer "):]
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

    Raises:
        ValueError: トークンが無効または期限切れの場合
    """
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
