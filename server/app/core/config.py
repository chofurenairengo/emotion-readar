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

    # CORS設定
    ALLOWED_ORIGINS: str = ""  # カンマ区切りのオリジンリスト

    # レート制限設定
    RATE_LIMIT_DEFAULT: int = 100  # デフォルト: 100 req/min
    RATE_LIMIT_WINDOW_SECONDS: int = 60  # ウィンドウ: 60秒

    # Pydanticの設定
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
    )

    @property
    def cors_origins(self) -> list[str]:
        """CORS許可オリジンのリストを返す。

        - ALLOWED_ORIGINSが設定されている場合はそれをパース
        - 開発環境（ENV_STATE=dev）では localhost を自動追加
        """
        origins: list[str] = []

        if self.ALLOWED_ORIGINS:
            origins = [
                origin.strip()
                for origin in self.ALLOWED_ORIGINS.split(",")
                if origin.strip()
            ]

        # 開発環境ではlocalhostを自動追加
        if self.ENV_STATE == "dev":
            dev_origins = [
                "http://localhost:3000",
                "http://localhost:8080",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:8080",
            ]
            # イミュータブルなパターンでリストを結合
            additional_origins = [o for o in dev_origins if o not in origins]
            origins = origins + additional_origins
        elif not origins:
            # 本番環境でオリジンが空の場合はエラー
            raise ValueError(
                "ALLOWED_ORIGINS must be set in production environment. "
                "Set ENV_STATE=dev for development mode."
            )

        return origins

    @property
    def rate_limits(self) -> dict[str, int]:
        """エンドポイントごとのレート制限値を返す。

        Returns:
            dict: パスパターン -> 1分あたりの最大リクエスト数
        """
        return {
            "/api/sessions": 30,  # セッション作成: 30 req/min
            "/api/sessions/*/end": 30,  # セッション終了: 30 req/min
            "/api/features": 100,  # 特徴量送信: 100 req/min
            "default": self.RATE_LIMIT_DEFAULT,  # デフォルト: 100 req/min
        }


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings
