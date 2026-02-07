"""Firebase Admin SDK の初期化モジュール。

環境変数:
    GOOGLE_APPLICATION_CREDENTIALS: サービスアカウントキーファイルのパス
    FIREBASE_SERVICE_ACCOUNT: サービスアカウントキーのJSON文字列（ファイル不要）
    FIREBASE_AUTH_EMULATOR_HOST: Auth Emulatorのホスト（例: localhost:9099）

Secret Manager:
    USE_SECRET_MANAGER=true の場合、FIREBASE_SERVICE_ACCOUNT は
    Secret Manager から取得されます。
"""

from __future__ import annotations

import json
import logging
import os
from typing import Any, cast

import firebase_admin
from firebase_admin import auth, credentials

from app.infra.secret_manager import get_secret

logger = logging.getLogger(__name__)

_app: firebase_admin.App | None = None


def initialize_firebase() -> firebase_admin.App:
    """Firebase Admin SDK を初期化し、App インスタンスを返す。

    既に初期化済みの場合は既存インスタンスを返す。

    認証情報の優先順位:
        1. Secret Manager（USE_SECRET_MANAGER=true の場合）
        2. FIREBASE_SERVICE_ACCOUNT 環境変数（JSON文字列）
        3. ADC（Application Default Credentials）
        4. 認証情報なし（エミュレータ利用時）
    """
    global _app

    if _app is not None:
        return _app

    if firebase_admin._apps:
        _app = firebase_admin.get_app()
        return _app

    emulator_host = os.environ.get("FIREBASE_AUTH_EMULATOR_HOST", "")
    if emulator_host:
        logger.info("Firebase Auth Emulator に接続: %s", emulator_host)

    project_id = os.environ.get("GCP_PROJECT_ID", "dev-project")
    cred = _build_credentials(emulator_host)

    _app = firebase_admin.initialize_app(
        credential=cred,
        options={"projectId": project_id},
    )
    logger.info("Firebase Admin SDK 初期化完了 (project=%s)", project_id)
    return _app


def get_firebase_app() -> firebase_admin.App:
    """初期化済みの Firebase App を取得する。未初期化の場合は初期化する。"""
    if _app is None:
        return initialize_firebase()
    return _app


def verify_id_token(id_token: str) -> dict[str, Any]:
    """Firebase ID Token を検証し、デコード済みクレームを返す。

    Args:
        id_token: クライアントから受け取った Firebase ID Token

    Returns:
        デコード済みトークンクレーム (uid, email 等を含む辞書)

    Raises:
        firebase_admin.auth.InvalidIdTokenError: トークンが無効な場合
        firebase_admin.auth.ExpiredIdTokenError: トークンが期限切れの場合
        firebase_admin.auth.RevokedIdTokenError: トークンが失効済みの場合
    """
    get_firebase_app()
    return cast(dict[str, Any], auth.verify_id_token(id_token))


def _build_credentials(emulator_host: str) -> credentials.Base | None:
    """環境に応じた認証情報を生成する。

    認証情報の優先順位:
        1. Secret Manager / FIREBASE_SERVICE_ACCOUNT（JSON文字列）
        2. ADC（Application Default Credentials）
        3. 認証情報なし（エミュレータ利用時）
    """
    # Secret Manager または環境変数から取得
    sa_json = get_secret("FIREBASE_SERVICE_ACCOUNT", default="")
    if sa_json:
        logger.info("FIREBASE_SERVICE_ACCOUNT からサービスアカウントを使用")
        try:
            sa_dict = json.loads(sa_json)
        except json.JSONDecodeError as exc:
            logger.error(
                "FIREBASE_SERVICE_ACCOUNT のJSONパースに失敗しました: %s",
                exc,
            )
            raise ValueError(
                "Invalid JSON in FIREBASE_SERVICE_ACCOUNT "
                "(from Secret Manager or environment variable)"
            ) from exc
        return credentials.Certificate(sa_dict)

    if emulator_host:
        logger.info("エミュレータモード: 認証情報なしで初期化")
        return None

    logger.info("Application Default Credentials (ADC) を使用")
    return credentials.ApplicationDefault()
