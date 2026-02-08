from __future__ import annotations

from google.auth.credentials import Credentials
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_google_vertexai import ChatVertexAI
from langchain_groq import ChatGroq

from app.core.config import get_settings


class LLMClientFactory:
    """Vertex AI経由でGemini(ft model)との接続を生成する工場.

    認証はADC（Application Default Credentials）を使用:
    - Cloud Run: サービスアカウントから自動取得
    - ローカル: gcloud auth application-default login
    - Docker: GOOGLE_APPLICATION_CREDENTIALS 環境変数
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
    def create_ft_client(
        credentials: Credentials | None = None,
    ) -> ChatVertexAI:
        settings = get_settings()
        model = LLMClientFactory._resolve_model_id(
            settings.FT_MODEL_ID,
            settings.GCP_PROJECT_ID,
            settings.GCP_LOCATION,
        )
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
    def create_client(
        credentials: Credentials | None = None,
    ) -> BaseChatModel:
        """設定に基づいてLLMクライアントを生成する.

        LLM_PROVIDER設定に基づいて適切なクライアントを返す:
        - "groq": Groq API (高速)
        - "gemini" or "": Vertex AI Gemini (FTモデル対応)

        Args:
            credentials: GCP認証情報。Vertex AI使用時に渡される。

        Raises:
            ValueError: 未知のLLM_PROVIDERが指定された場合
        """
        settings = get_settings()
        if settings.LLM_PROVIDER == "groq":
            return LLMClientFactory.create_groq_client()
        if settings.LLM_PROVIDER in ("", "gemini"):
            return LLMClientFactory.create_ft_client(credentials=credentials)
        raise ValueError(
            f"Unknown LLM_PROVIDER: '{settings.LLM_PROVIDER}'. "
            "Must be 'gemini' or 'groq'."
        )
