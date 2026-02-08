"""GCP ADC（Application Default Credentials）認証モジュール.

アプリケーション起動時にADC認証を一括実行し、
各GCPサービス（STT、Vertex AI等）で共有するクレデンシャルを管理する。

認証情報の解決順序（google.auth.default の仕様）:
    1. GOOGLE_APPLICATION_CREDENTIALS 環境変数のキーファイル
    2. gcloud auth application-default login のキャッシュ
    3. Cloud Run 等のメタデータサーバー（自動）
"""

from __future__ import annotations

import logging

from google.auth import default as google_auth_default
from google.auth.credentials import Credentials
from google.auth.transport.requests import Request

logger = logging.getLogger(__name__)

_credentials: Credentials | None = None
_project_id: str | None = None


def initialize_credentials() -> tuple[Credentials, str | None]:
    """ADCで認証情報を取得し、トークンを事前リフレッシュする.

    Returns:
        (credentials, project_id) のタプル

    Raises:
        google.auth.exceptions.DefaultCredentialsError: 認証情報が見つからない場合
    """
    global _credentials, _project_id

    credentials, project = google_auth_default(
        scopes=["https://www.googleapis.com/auth/cloud-platform"]
    )
    credentials.refresh(Request())

    _credentials = credentials
    _project_id = project

    logger.info("ADC認証完了 (project=%s)", project)
    return credentials, project


def get_credentials() -> Credentials | None:
    """共有クレデンシャルを取得する.

    initialize_credentials() が呼ばれていない場合は None を返す。
    """
    return _credentials


def get_project_id() -> str | None:
    """ADCから取得したプロジェクトIDを返す."""
    return _project_id
