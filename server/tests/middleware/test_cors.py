import pytest

from app.core.config import Settings


class TestCorsOrigins:
    """cors_originsプロパティのテスト"""

    def test_dev_environment_includes_localhost(self) -> None:
        """開発環境ではlocalhostが自動追加される"""
        settings = Settings(
            GCP_PROJECT_ID="test-project",
            FT_MODEL_ID="gemini-1.5-flash",
            ENV_STATE="dev",
            ALLOWED_ORIGINS="",
        )

        origins = settings.cors_origins

        assert "http://localhost:3000" in origins
        assert "http://localhost:8080" in origins
        assert "http://127.0.0.1:3000" in origins
        assert "http://127.0.0.1:8080" in origins

    def test_prod_environment_raises_error_when_origins_empty(self) -> None:
        """本番環境でALLOWED_ORIGINSが空の場合はエラー"""
        settings = Settings(
            GCP_PROJECT_ID="test-project",
            FT_MODEL_ID="gemini-1.5-flash",
            ENV_STATE="prod",
            ALLOWED_ORIGINS="",
        )

        with pytest.raises(ValueError) as exc_info:
            _ = settings.cors_origins

        assert "ALLOWED_ORIGINS must be set in production" in str(exc_info.value)

    def test_allowed_origins_parsing(self) -> None:
        """カンマ区切りのALLOWED_ORIGINSがパースされる"""
        settings = Settings(
            GCP_PROJECT_ID="test-project",
            FT_MODEL_ID="gemini-1.5-flash",
            ENV_STATE="prod",
            ALLOWED_ORIGINS="https://example.com,https://app.example.com",
        )

        origins = settings.cors_origins

        assert "https://example.com" in origins
        assert "https://app.example.com" in origins
        assert len(origins) == 2

    def test_allowed_origins_with_spaces(self) -> None:
        """スペースを含むALLOWED_ORIGINSがトリムされる"""
        settings = Settings(
            GCP_PROJECT_ID="test-project",
            FT_MODEL_ID="gemini-1.5-flash",
            ENV_STATE="prod",
            ALLOWED_ORIGINS="https://example.com , https://app.example.com",
        )

        origins = settings.cors_origins

        assert "https://example.com" in origins
        assert "https://app.example.com" in origins

    def test_dev_environment_with_custom_origins(self) -> None:
        """開発環境でカスタムオリジンとlocalhostが両方含まれる"""
        settings = Settings(
            GCP_PROJECT_ID="test-project",
            FT_MODEL_ID="gemini-1.5-flash",
            ENV_STATE="dev",
            ALLOWED_ORIGINS="https://staging.example.com",
        )

        origins = settings.cors_origins

        assert "https://staging.example.com" in origins
        assert "http://localhost:3000" in origins

    def test_empty_origin_in_list_ignored(self) -> None:
        """空のオリジンは無視される"""
        settings = Settings(
            GCP_PROJECT_ID="test-project",
            FT_MODEL_ID="gemini-1.5-flash",
            ENV_STATE="prod",
            ALLOWED_ORIGINS="https://example.com,,https://app.example.com,",
        )

        origins = settings.cors_origins

        assert len(origins) == 2
        assert "" not in origins
