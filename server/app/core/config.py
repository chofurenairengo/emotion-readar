from functools import lru_cache

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

    # 将来的にAWS用の設定もここに追加可能
    # AWS_REGION: str = "ap-northeast-1"

    # Pydanticの設定
    model_config = SettingsConfigDict(
        env_file=".env",             # 読み込むファイル名
        env_file_encoding="utf-8",   # 文字コード
        case_sensitive=True          # 大文字小文字を区別する (GEMINI_API_KEY != gemini_api_key)
    )

# @lru_cache: 設定を何度も読み込まないようにキャッシュする（高速化）
@lru_cache
def get_settings():
    return Settings()

# アプリ全体で使うインスタンス
settings = get_settings()
