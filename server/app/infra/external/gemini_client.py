from langchain_google_vertexai import ChatVertexAI

from app.core.config import get_settings


class LLMClientFactory:
    """Vertex AI経由でGeminiとの接続を生成する工場.

    認証はサービスアカウント方式を使用:
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
    def create_ft_client() -> ChatVertexAI:
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
        )
