"""
sync_to_drive.py - 仕様書をGoogle Driveに同期

環境変数:
- GOOGLE_DRIVE_CREDENTIALS: サービスアカウントキーJSON（文字列）
- GOOGLE_DRIVE_FOLDER_ID: 同期先フォルダID

同期対象:
- SPECIFICATION.md
- docs/spec/*.md
"""

import json
import os
import sys
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload


def get_drive_service():
    """Google Drive APIサービスを取得"""
    credentials_json = os.environ.get("GOOGLE_DRIVE_CREDENTIALS")
    if not credentials_json:
        print("Error: GOOGLE_DRIVE_CREDENTIALS not set")
        sys.exit(1)

    credentials_info = json.loads(credentials_json)
    credentials = service_account.Credentials.from_service_account_info(
        credentials_info, scopes=["https://www.googleapis.com/auth/drive.file"]
    )

    return build("drive", "v3", credentials=credentials)


def find_file_in_folder(service, folder_id: str, file_name: str) -> str | None:
    """フォルダ内のファイルをファイル名で検索"""
    query = f"name='{file_name}' and '{folder_id}' in parents and trashed=false"
    results = service.files().list(q=query, fields="files(id, name)").execute()
    files = results.get("files", [])
    return files[0]["id"] if files else None


def upload_or_update_file(
    service, folder_id: str, local_path: Path, drive_name: str
) -> str:
    """ファイルをアップロードまたは更新"""
    file_metadata = {"name": drive_name, "parents": [folder_id]}
    media = MediaFileUpload(
        str(local_path), mimetype="text/markdown", resumable=True
    )

    existing_file_id = find_file_in_folder(service, folder_id, drive_name)

    if existing_file_id:
        # 既存ファイルを更新
        file = (
            service.files()
            .update(fileId=existing_file_id, media_body=media)
            .execute()
        )
        print(f"Updated: {drive_name} (ID: {file['id']})")
    else:
        # 新規作成
        file = (
            service.files()
            .create(body=file_metadata, media_body=media, fields="id")
            .execute()
        )
        print(f"Created: {drive_name} (ID: {file['id']})")

    return file["id"]


def create_folder_if_not_exists(
    service, parent_folder_id: str, folder_name: str
) -> str:
    """フォルダがなければ作成"""
    query = (
        f"name='{folder_name}' and '{parent_folder_id}' in parents "
        f"and mimeType='application/vnd.google-apps.folder' and trashed=false"
    )
    results = service.files().list(q=query, fields="files(id)").execute()
    files = results.get("files", [])

    if files:
        return files[0]["id"]

    file_metadata = {
        "name": folder_name,
        "mimeType": "application/vnd.google-apps.folder",
        "parents": [parent_folder_id],
    }
    folder = service.files().create(body=file_metadata, fields="id").execute()
    print(f"Created folder: {folder_name} (ID: {folder['id']})")
    return folder["id"]


def main():
    folder_id = os.environ.get("GOOGLE_DRIVE_FOLDER_ID")
    if not folder_id:
        print("Error: GOOGLE_DRIVE_FOLDER_ID not set")
        sys.exit(1)

    # リポジトリルートを特定
    repo_root = Path(__file__).parent.parent.parent

    # 同期対象ファイル
    files_to_sync = [
        (repo_root / "SPECIFICATION.md", "SPECIFICATION.md"),
    ]

    # docs/spec/*.md を追加
    spec_dir = repo_root / "docs" / "spec"
    if spec_dir.exists():
        for spec_file in spec_dir.glob("*.md"):
            files_to_sync.append((spec_file, f"spec/{spec_file.name}"))

    service = get_drive_service()

    # specサブフォルダを作成
    spec_folder_id = create_folder_if_not_exists(service, folder_id, "spec")

    # ファイルを同期
    for local_path, drive_path in files_to_sync:
        if not local_path.exists():
            print(f"Warning: {local_path} not found, skipping")
            continue

        if drive_path.startswith("spec/"):
            # specサブフォルダ内に配置
            drive_name = drive_path.replace("spec/", "")
            upload_or_update_file(service, spec_folder_id, local_path, drive_name)
        else:
            # ルートフォルダに配置
            upload_or_update_file(service, folder_id, local_path, drive_path)

    print("\nSync completed successfully!")


if __name__ == "__main__":
    main()
