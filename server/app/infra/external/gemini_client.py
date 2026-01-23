from langchain_google_genai import ChatGoogleGenerativeAI

from app.core.config import get_settings


class LLMClientFactory:
    """Gemini APIとの接続を生成する工場."""

    @staticmethod
    def create_ft_client() -> ChatGoogleGenerativeAI:
        settings = get_settings()
        return ChatGoogleGenerativeAI(
            model=settings.FT_MODEL_ID,
            google_api_key=settings.GEMINI_API_KEY,
            temperature=0.5,
            max_retries=2,
        )
