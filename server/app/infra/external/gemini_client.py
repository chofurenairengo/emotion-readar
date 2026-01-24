from langchain_google_vertexai import ChatVertexAI

from app.core.config import get_settings


class LLMClientFactory:
    """Vertex AI経由でGeminiとの接続を生成する工場.

    認証はサービスアカウント方式を使用:
    - ローカル: GOOGLE_APPLICATION_CREDENTIALS環境変数でJSONキーを指定
    - Cloud Run: 自動でサービスアカウントから認証情報を取得
    """

    @staticmethod
    def create_ft_client() -> ChatVertexAI:
        settings = get_settings()
        return ChatVertexAI(
            model=settings.FT_MODEL_ID,
            project=settings.GCP_PROJECT_ID,
            location=settings.GCP_LOCATION,
            temperature=0.5,
            max_retries=2,
        )
