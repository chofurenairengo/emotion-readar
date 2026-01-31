"""Google Cloud Secret Manager 連携モジュール。

環境変数:
    USE_SECRET_MANAGER: Secret Managerを使用するかどうか（true/false）
    GCP_PROJECT_ID: GCPプロジェクトID
    ENV_STATE: 環境状態（dev/prod）

使用例:
    secret = get_secret("FIREBASE_SERVICE_ACCOUNT")

注意:
    このモジュールは Settings 初期化前に使用できるよう、
    os.environ から直接環境変数を読み取ります。
"""

from __future__ import annotations

import logging
import os
from functools import lru_cache
from typing import TYPE_CHECKING

from google.api_core.exceptions import GoogleAPIError

if TYPE_CHECKING:
    from google.cloud.secretmanager import SecretManagerServiceClient

logger = logging.getLogger(__name__)

_MASK_VISIBLE_CHARS = 4


def _mask(value: str) -> str:
    """文字列をマスキングする（先頭数文字 + ****）。"""
    if len(value) <= _MASK_VISIBLE_CHARS:
        return "****"
    return value[:_MASK_VISIBLE_CHARS] + "****"


@lru_cache(maxsize=1)
def _get_client() -> SecretManagerServiceClient:
    """Secret Manager クライアントを取得する（シングルトン）。"""
    from google.cloud import secretmanager

    logger.info("Secret Manager クライアントを初期化しました")
    return secretmanager.SecretManagerServiceClient()


@lru_cache(maxsize=32)
def _fetch_from_secret_manager(secret_id: str, project_id: str) -> str:
    """Secret Managerからシークレットを取得する（キャッシュ付き）。

    Args:
        secret_id: シークレットの名前（例: FIREBASE_SERVICE_ACCOUNT）
        project_id: GCPプロジェクトID

    Returns:
        シークレットの値

    Raises:
        google.api_core.exceptions.NotFound: シークレットが存在しない場合
        google.api_core.exceptions.PermissionDenied: 権限がない場合
    """
    client = _get_client()
    name = f"projects/{project_id}/secrets/{secret_id}/versions/latest"

    response = client.access_secret_version(request={"name": name})
    secret_value = response.payload.data.decode("UTF-8")

    logger.debug("Secret Manager からシークレットを取得: %s", _mask(secret_id))
    return secret_value


def get_secret(secret_id: str, default: str | None = None) -> str | None:
    """シークレットを取得する。

    取得優先順位:
        1. USE_SECRET_MANAGER=true の場合: Secret Managerから取得
        2. 環境変数から取得（フォールバック）
        3. デフォルト値を返す

    Args:
        secret_id: シークレットの名前（例: FIREBASE_SERVICE_ACCOUNT）
        default: シークレットが見つからない場合のデフォルト値

    Returns:
        シークレットの値、または見つからない場合はdefault

    Raises:
        ValueError: 本番環境でシークレットが見つからない場合

    Note:
        このメソッドは Settings 初期化前に使用できるよう、
        os.environ から直接環境変数を読み取ります。
    """
    use_secret_manager = os.environ.get("USE_SECRET_MANAGER", "false").lower() == "true"
    env_state = os.environ.get("ENV_STATE", "dev")
    gcp_project_id = os.environ.get("GCP_PROJECT_ID", "")

    # Secret Managerが有効な場合
    if use_secret_manager:
        if not gcp_project_id:
            logger.warning(
                "USE_SECRET_MANAGER=true ですが GCP_PROJECT_ID が設定されていません。"
                "環境変数にフォールバックします。"
            )
        else:
            try:
                return _fetch_from_secret_manager(secret_id, gcp_project_id)
            except GoogleAPIError as e:
                logger.warning(
                    "Secret Manager からの取得に失敗: %s (error_type: %s)。"
                    " 環境変数にフォールバックします。",
                    _mask(secret_id),
                    type(e).__name__,
                )

    # 環境変数フォールバック
    env_value = os.environ.get(secret_id)
    if env_value:
        logger.debug("環境変数からシークレットを取得: %s", _mask(secret_id))
        return env_value

    # デフォルト値
    if default is not None:
        return default

    # 本番環境でシークレットが見つからない場合はエラー
    if env_state != "dev":
        raise ValueError(
            f"Secret '{_mask(secret_id)}' not found. "
            f"Set USE_SECRET_MANAGER=true and configure Secret Manager, "
            f"or set the corresponding environment variable."
        )

    return None


def clear_cache() -> None:
    """シークレットのキャッシュをクリアする（テスト用）。

    Warning:
        この関数は主にテスト用です。本番環境での使用は推奨されません。
    """
    # 本番で呼ばれた場合のみ警告
    if os.environ.get("ENV_STATE", "dev") != "dev":
        import warnings

        warnings.warn(
            "clear_cache() はテスト用です。本番環境での使用は推奨されません。",
            stacklevel=2,
        )
    _fetch_from_secret_manager.cache_clear()
    _get_client.cache_clear()
