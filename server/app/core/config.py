from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    アプリケーション全体の設定管理クラス
    .envファイルの値を自動で読み込みます
    """

    # GCP設定 (サービスアカウント認証: jsonkey -> アクセストークン取得)
    GCP_PROJECT_ID: str  # GCPプロジェクトID
    GCP_LOCATION: str = "asia-northeast1"  # Vertex AIのリージョン

    # モデル設定
    FT_MODEL_ID: str  # Geminiモデル名 (例: gemini-1.5-flash)

    # 任意項目
    ENV_STATE: str = "dev"
    PROJECT_NAME: str = "ERA"

    # Pydanticの設定
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
    )


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings
