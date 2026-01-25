from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import get_settings


def apply_cors(app: FastAPI) -> None:
    """FastAPIアプリケーションにCORSミドルウェアを適用する。

    許可オリジンは環境変数 ALLOWED_ORIGINS から取得。
    開発環境では localhost が自動的に許可される。
    """
    settings = get_settings()
    origins = settings.cors_origins

    app.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"],
        allow_headers=[
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With",
            "X-Request-ID",
        ],
    )
