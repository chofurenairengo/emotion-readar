from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    アプリケーション全体の設定管理クラス
    .envファイルの値を自動で読み込みます
    """

    # 必須項目 (宣言のみでデフォルト値なし = .envに無いと起動時にエラーになる)
    GEMINI_API_KEY: str
    FT_MODEL_ID: str

    # 任意項目 (デフォルト値あり)
    ENV_STATE: str = "dev"
    PROJECT_NAME: str = "Comm-XR"

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
