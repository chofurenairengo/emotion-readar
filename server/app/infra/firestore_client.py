"""Firestore クライアント初期化モジュール。

環境変数:
    GCP_PROJECT_ID: GCPプロジェクトID（デフォルト: dev-project）
    FIRESTORE_EMULATOR_HOST: Firestore Emulatorのホスト（例: localhost:8080）
"""

from __future__ import annotations

import os
from functools import lru_cache

from google.cloud import firestore  # type: ignore[attr-defined,unused-ignore]


@lru_cache(maxsize=1)
def get_firestore_client() -> firestore.Client:
    """Firestoreクライアントのシングルトン取得。

    FIRESTORE_EMULATOR_HOST 環境変数が設定されている場合、
    自動的にEmulatorに接続する（google-cloud-firestoreの標準動作）。

    Returns:
        Firestore クライアントインスタンス
    """
    project_id = os.getenv("GCP_PROJECT_ID", "dev-project")
    return firestore.Client(project=project_id)
