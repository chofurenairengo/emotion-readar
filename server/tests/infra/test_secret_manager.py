from unittest.mock import MagicMock, patch

import pytest
from google.api_core.exceptions import GoogleAPIError

from app.infra.secret_manager import (
    _fetch_from_secret_manager,
    _get_client,
    _mask,
    clear_cache,
    get_secret,
)


class TestMask:
    """_mask関数のテスト"""

    def test_mask_empty_string(self) -> None:
        """空文字列は****を返す"""
        assert _mask("") == "****"

    def test_mask_short_string(self) -> None:
        """4文字未満は****を返す"""
        assert _mask("ABC") == "****"

    def test_mask_exactly_four_chars(self) -> None:
        """ちょうど4文字は****を返す"""
        assert _mask("ABCD") == "****"

    def test_mask_five_chars(self) -> None:
        """5文字は先頭4文字+****を返す"""
        assert _mask("ABCDE") == "ABCD****"

    def test_mask_long_string(self) -> None:
        """長い文字列は先頭4文字+****を返す"""
        assert _mask("FIREBASE_SERVICE_ACCOUNT") == "FIRE****"


class TestGetSecret:
    """get_secret関数のテスト"""

    def setup_method(self) -> None:
        """各テスト前にキャッシュをクリア"""
        clear_cache()

    def test_returns_env_var_when_secret_manager_disabled(self) -> None:
        """Secret Manager無効時は環境変数から取得"""
        with patch.dict(
            "os.environ",
            {
                "USE_SECRET_MANAGER": "false",
                "ENV_STATE": "dev",
                "TEST_SECRET": "env_value",
            },
        ):
            result = get_secret("TEST_SECRET")

        assert result == "env_value"

    def test_returns_default_when_not_found_in_dev(self) -> None:
        """開発環境でシークレットが見つからない場合はNoneを返す"""
        with patch.dict(
            "os.environ",
            {"USE_SECRET_MANAGER": "false", "ENV_STATE": "dev"},
            clear=True,
        ):
            result = get_secret("NONEXISTENT_SECRET")

        assert result is None

    def test_returns_explicit_default(self) -> None:
        """明示的なデフォルト値がある場合はそれを返す"""
        with patch.dict(
            "os.environ",
            {"USE_SECRET_MANAGER": "false", "ENV_STATE": "dev"},
            clear=True,
        ):
            result = get_secret("NONEXISTENT_SECRET", default="default_value")

        assert result == "default_value"

    def test_raises_error_in_prod_when_not_found(self) -> None:
        """本番環境でシークレットが見つからない場合はエラー"""
        with patch.dict(
            "os.environ",
            {"USE_SECRET_MANAGER": "false", "ENV_STATE": "prod"},
            clear=True,
        ):
            with pytest.raises(ValueError, match="Secret 'MISSING_SECRET' not found"):
                get_secret("MISSING_SECRET")

    @patch("app.infra.secret_manager._fetch_from_secret_manager")
    def test_fetches_from_secret_manager_when_enabled(
        self, mock_fetch: MagicMock
    ) -> None:
        """Secret Manager有効時はSecret Managerから取得"""
        mock_fetch.return_value = "secret_from_sm"

        with patch.dict(
            "os.environ",
            {
                "USE_SECRET_MANAGER": "true",
                "GCP_PROJECT_ID": "test-project",
                "ENV_STATE": "prod",
            },
        ):
            result = get_secret("MY_SECRET")

        assert result == "secret_from_sm"
        mock_fetch.assert_called_once_with("MY_SECRET", "test-project")

    @patch("app.infra.secret_manager._fetch_from_secret_manager")
    def test_falls_back_to_env_when_secret_manager_fails(
        self, mock_fetch: MagicMock
    ) -> None:
        """Secret Managerが失敗した場合は環境変数にフォールバック"""
        mock_fetch.side_effect = GoogleAPIError("Network error")

        with patch.dict(
            "os.environ",
            {
                "USE_SECRET_MANAGER": "true",
                "GCP_PROJECT_ID": "test-project",
                "ENV_STATE": "dev",
                "MY_SECRET": "env_fallback",
            },
        ):
            result = get_secret("MY_SECRET")

        assert result == "env_fallback"

    def test_falls_back_to_env_when_gcp_project_id_missing(self) -> None:
        """GCP_PROJECT_IDが未設定の場合は環境変数にフォールバック"""
        with patch.dict(
            "os.environ",
            {
                "USE_SECRET_MANAGER": "true",
                "GCP_PROJECT_ID": "",
                "ENV_STATE": "dev",
                "MY_SECRET": "env_fallback",
            },
        ):
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
