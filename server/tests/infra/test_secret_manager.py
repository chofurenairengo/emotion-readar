from unittest.mock import MagicMock, patch

import pytest
from google.api_core.exceptions import GoogleAPIError

from app.infra.secret_manager import (
    _fetch_from_secret_manager,
    _get_client,
    clear_cache,
    get_secret,
)


class TestGetSecret:
    """get_secret関数のテスト"""

    def setup_method(self) -> None:
        """各テスト前にキャッシュをクリア"""
        clear_cache()

    @patch("app.infra.secret_manager.get_settings")
    def test_returns_env_var_when_secret_manager_disabled(
        self, mock_get_settings: MagicMock
    ) -> None:
        """Secret Manager無効時は環境変数から取得"""
        mock_settings = MagicMock()
        mock_settings.USE_SECRET_MANAGER = False
        mock_settings.ENV_STATE = "dev"
        mock_get_settings.return_value = mock_settings

        with patch.dict("os.environ", {"TEST_SECRET": "env_value"}):
            result = get_secret("TEST_SECRET")

        assert result == "env_value"

    @patch("app.infra.secret_manager.get_settings")
    def test_returns_default_when_not_found_in_dev(
        self, mock_get_settings: MagicMock
    ) -> None:
        """開発環境でシークレットが見つからない場合はNoneを返す"""
        mock_settings = MagicMock()
        mock_settings.USE_SECRET_MANAGER = False
        mock_settings.ENV_STATE = "dev"
        mock_get_settings.return_value = mock_settings

        with patch.dict("os.environ", {}, clear=True):
            result = get_secret("NONEXISTENT_SECRET")

        assert result is None

    @patch("app.infra.secret_manager.get_settings")
    def test_returns_explicit_default(
        self, mock_get_settings: MagicMock
    ) -> None:
        """明示的なデフォルト値がある場合はそれを返す"""
        mock_settings = MagicMock()
        mock_settings.USE_SECRET_MANAGER = False
        mock_settings.ENV_STATE = "dev"
        mock_get_settings.return_value = mock_settings

        with patch.dict("os.environ", {}, clear=True):
            result = get_secret("NONEXISTENT_SECRET", default="default_value")

        assert result == "default_value"

    @patch("app.infra.secret_manager.get_settings")
    def test_raises_error_in_prod_when_not_found(
        self, mock_get_settings: MagicMock
    ) -> None:
        """本番環境でシークレットが見つからない場合はエラー"""
        mock_settings = MagicMock()
        mock_settings.USE_SECRET_MANAGER = False
        mock_settings.ENV_STATE = "prod"
        mock_get_settings.return_value = mock_settings

        with patch.dict("os.environ", {}, clear=True):
            with pytest.raises(ValueError, match="Secret 'MISSING_SECRET' not found"):
                get_secret("MISSING_SECRET")

    @patch("app.infra.secret_manager._fetch_from_secret_manager")
    @patch("app.infra.secret_manager.get_settings")
    def test_fetches_from_secret_manager_when_enabled(
        self,
        mock_get_settings: MagicMock,
        mock_fetch: MagicMock,
    ) -> None:
        """Secret Manager有効時はSecret Managerから取得"""
        mock_settings = MagicMock()
        mock_settings.USE_SECRET_MANAGER = True
        mock_settings.GCP_PROJECT_ID = "test-project"
        mock_settings.ENV_STATE = "prod"
        mock_get_settings.return_value = mock_settings
        mock_fetch.return_value = "secret_from_sm"

        result = get_secret("MY_SECRET")

        assert result == "secret_from_sm"
        mock_fetch.assert_called_once_with("MY_SECRET", "test-project")

    @patch("app.infra.secret_manager._fetch_from_secret_manager")
    @patch("app.infra.secret_manager.get_settings")
    def test_falls_back_to_env_when_secret_manager_fails(
        self,
        mock_get_settings: MagicMock,
        mock_fetch: MagicMock,
    ) -> None:
        """Secret Manager取得失敗時は環境変数にフォールバック"""
        mock_settings = MagicMock()
        mock_settings.USE_SECRET_MANAGER = True
        mock_settings.GCP_PROJECT_ID = "test-project"
        mock_settings.ENV_STATE = "dev"
        mock_get_settings.return_value = mock_settings
        mock_fetch.side_effect = GoogleAPIError("Network error")

        with patch.dict("os.environ", {"MY_SECRET": "env_fallback"}):
            result = get_secret("MY_SECRET")

        assert result == "env_fallback"


class TestFetchFromSecretManager:
    """_fetch_from_secret_manager のキャッシュ動作テスト"""

    def setup_method(self) -> None:
        """各テスト前にキャッシュをクリア"""
        clear_cache()

    @patch("app.infra.secret_manager._get_client")
    def test_caches_secret_values(self, mock_get_client: MagicMock) -> None:
        """同じシークレットは2回目以降キャッシュから返される"""
        mock_client = MagicMock()
        mock_response = MagicMock()
        mock_response.payload.data.decode.return_value = "cached_value"
        mock_client.access_secret_version.return_value = mock_response
        mock_get_client.return_value = mock_client

        # 2回呼び出し
        result1 = _fetch_from_secret_manager("SECRET_ID", "project-id")
        result2 = _fetch_from_secret_manager("SECRET_ID", "project-id")

        assert result1 == result2 == "cached_value"
        # キャッシュにより1回だけ呼ばれる
        assert mock_client.access_secret_version.call_count == 1

    @patch("app.infra.secret_manager._get_client")
    def test_different_secrets_not_cached(
        self, mock_get_client: MagicMock
    ) -> None:
        """異なるシークレットはそれぞれ個別に取得される"""
        mock_client = MagicMock()
        mock_response1 = MagicMock()
        mock_response1.payload.data.decode.return_value = "secret1"
        mock_response2 = MagicMock()
        mock_response2.payload.data.decode.return_value = "secret2"
        mock_client.access_secret_version.side_effect = [
            mock_response1,
            mock_response2,
        ]
        mock_get_client.return_value = mock_client

        result1 = _fetch_from_secret_manager("SECRET_1", "project-id")
        result2 = _fetch_from_secret_manager("SECRET_2", "project-id")

        assert result1 == "secret1"
        assert result2 == "secret2"
        assert mock_client.access_secret_version.call_count == 2


class TestGetClient:
    """_get_client のシングルトン動作テスト"""

    def setup_method(self) -> None:
        """各テスト前にキャッシュをクリア"""
        clear_cache()

    @patch("google.cloud.secretmanager.SecretManagerServiceClient")
    def test_singleton_client(
        self, mock_client_class: MagicMock
    ) -> None:
        """クライアントは一度だけ初期化される"""
        mock_client = MagicMock()
        mock_client_class.return_value = mock_client

        # 2回呼び出し
        client1 = _get_client()
        client2 = _get_client()

        assert client1 is client2
        # シングルトンにより1回だけ初期化される
        assert mock_client_class.call_count == 1
