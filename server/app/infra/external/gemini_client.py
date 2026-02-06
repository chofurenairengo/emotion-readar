from google.oauth2 import service_account
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_google_vertexai import ChatVertexAI
from langchain_groq import ChatGroq

from app.core.config import get_settings


class LLMClientFactory:
    """Vertex AI経由でGemini(ft model)との接続を生成する工場.

    認証はサービスアカウント方式を使用:
    - VERTEX_CREDENTIALS_PATH: 指定されている場合はそのキーファイルを使用
    - ローカル: GOOGLE_APPLICATION_CREDENTIALS環境変数でJSONキーを指定
    - Cloud Run: 自動でサービスアカウントから認証情報を取得
    """

    @staticmethod
    def _resolve_model_id(model_id: str, project: str, location: str) -> str:
        """モデルIDを適切な形式に変換する.

        - 数値のみの場合: エンドポイント形式に変換
        - projects/で始まる場合: そのまま使用
        - その他: パブリッシャーモデル名として使用
        """
        if model_id.isdigit():
            return f"projects/{project}/locations/{location}/endpoints/{model_id}"
        return model_id

    @staticmethod
    def _load_credentials() -> service_account.Credentials | None:
        """Vertex AI用の認証情報を読み込む."""
        settings = get_settings()
        if settings.VERTEX_CREDENTIALS_PATH:
            return service_account.Credentials.from_service_account_file(  # type: ignore[no-any-return]
                settings.VERTEX_CREDENTIALS_PATH
            )
        return None

    @staticmethod
    def create_ft_client() -> ChatVertexAI:
        settings = get_settings()
        model = LLMClientFactory._resolve_model_id(
            settings.FT_MODEL_ID,
            settings.GCP_PROJECT_ID,
            settings.GCP_LOCATION,
        )
        credentials = LLMClientFactory._load_credentials()
        return ChatVertexAI(
            model=model,
            project=settings.GCP_PROJECT_ID,
            location=settings.GCP_LOCATION,
            temperature=settings.LLM_TEMPERATURE,
            max_retries=2,
            credentials=credentials,
            response_mime_type="application/json",
        )

    @staticmethod
    def create_groq_client() -> ChatGroq:
        """Groq APIクライアントを生成する."""
        settings = get_settings()
        return ChatGroq(
            groq_api_key=settings.GROQ_API_KEY,  # type: ignore[arg-type]
            model_name=settings.GROQ_MODEL,
            temperature=settings.LLM_TEMPERATURE,
        )

    @staticmethod
    def create_client() -> BaseChatModel:
        """設定に基づいてLLMクライアントを生成する.

        LLM_PROVIDER設定に基づいて適切なクライアントを返す:
        - "groq": Groq API (高速)
        - "gemini" or "": Vertex AI Gemini (FTモデル対応)

        Raises:
            ValueError: 未知のLLM_PROVIDERが指定された場合
        """
        settings = get_settings()
        if settings.LLM_PROVIDER == "groq":
            return LLMClientFactory.create_groq_client()
        if settings.LLM_PROVIDER in ("", "gemini"):
            return LLMClientFactory.create_ft_client()
        raise ValueError(
            f"Unknown LLM_PROVIDER: '{settings.LLM_PROVIDER}'. "
            "Must be 'gemini' or 'groq'."
        )
