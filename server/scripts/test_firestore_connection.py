"""Firestore 接続テストスクリプト"""

import os
import sys

from dotenv import load_dotenv
from google.cloud import firestore


def test_connection() -> None:
    """Firestore への接続をテストする."""
    # .env ファイルを読み込み
    load_dotenv()

    project_id = os.getenv("GCP_PROJECT_ID")
    if not project_id:
        print("ERROR: GCP_PROJECT_ID is not set")
        sys.exit(1)

    print(f"Connecting to project: {project_id}")

    try:
        client = firestore.Client(project=project_id)

        # コレクション一覧を取得（読み取り権限テスト）
        collections = list(client.collections())
        print(f"Found {len(collections)} collections")
        for col in collections:
            print(f"  - {col.id}")

        print("\n✓ Connection successful!")

    except Exception as e:
        print(f"\n✗ Connection failed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    test_connection()
