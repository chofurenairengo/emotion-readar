from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    アプリケーション全体の設定管理クラス
    .envファイルの値を自動で読み込みます
    """

    # GCP設定（認証はADCで一括管理）
    GCP_PROJECT_ID: str  # GCPプロジェクトID
    GCP_LOCATION: str = "asia-northeast1"  # Vertex AIのリージョン

    # モデル設定
    FT_MODEL_ID: str = ""  # Geminiモデル名（LLM_PROVIDER=gemini時に必須）
    LLM_TEMPERATURE: float = 0.7  # LLMの温度パラメータ（0.0〜1.0, デフォルト: 0.7）

    # LLMプロバイダー設定
    LLM_PROVIDER: str = ""  # "gemini" or "groq"
    GROQ_API_KEY: str = ""  # Groq APIキー（LLM_PROVIDER=groq時に必須）
    GROQ_MODEL: str = ""  # Groqモデル名

    @model_validator(mode="after")
    def validate_ft_model_id(self) -> "Settings":
        """LLM_PROVIDERがgeminiまたは未設定の場合、FT_MODEL_IDが必須."""
        if self.LLM_PROVIDER in ("", "gemini") and not self.FT_MODEL_ID:
            raise ValueError(
                "FT_MODEL_ID must be set when LLM_PROVIDER is "
                f"'{self.LLM_PROVIDER}' (gemini). "
                "Set FT_MODEL_ID in .env or set LLM_PROVIDER='groq' "
                "to use Groq instead."
            )
        return self

    @model_validator(mode="after")
    def validate_groq_settings(self) -> "Settings":
        """LLM_PROVIDER='groq'の場合、GROQ_API_KEYとGROQ_MODELが必須."""
        if self.LLM_PROVIDER == "groq":
            missing: list[str] = []
            if not self.GROQ_API_KEY.strip():
                missing.append("GROQ_API_KEY")
            if not self.GROQ_MODEL.strip():
                missing.append("GROQ_MODEL")
            if missing:
                raise ValueError(f"LLM_PROVIDER='groq' requires: {', '.join(missing)}")
        return self

    # 任意項目
    ENV_STATE: str = "dev"
    PROJECT_NAME: str = "ERA"

    # 開発用認証バイパス（本番環境では絶対にtrueにしないこと）
    DEV_AUTH_BYPASS: bool = False

    # CORS設定
    ALLOWED_ORIGINS: str = ""  # カンマ区切りのオリジンリスト

    # レート制限設定
    RATE_LIMIT_DEFAULT: int = 100  # デフォルト: 100 req/min
    RATE_LIMIT_WINDOW_SECONDS: int = 60  # ウィンドウ: 60秒

    # ヘルスチェック設定
    HEALTH_CHECK_MODEL_ENABLED: bool = True  # モデル疎通確認の有効/無効
    HEALTH_CHECK_MODEL_CACHE_TTL: int = 30  # モデル疎通確認結果のキャッシュ時間（秒）

    # Secret Manager設定
    USE_SECRET_MANAGER: bool = False  # Secret Managerを使用するかどうか

    # Pydanticの設定
    model_config = SettingsConfigDict(
        env_file="../.env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore",  # .envの未定義フィールドを無視
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
            "default": self.RATE_LIMIT_DEFAULT,  # デフォルト: 100 req/min
        }


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings
