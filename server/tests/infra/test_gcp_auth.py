from collections.abc import Iterator
from unittest.mock import MagicMock, patch

import pytest
from google.auth.exceptions import DefaultCredentialsError

from app.infra import gcp_auth


@pytest.fixture(autouse=True)
def _reset_module_state() -> Iterator[None]:
    """各テスト前にモジュールレベルの状態をリセットする."""
    gcp_auth._credentials = None
    gcp_auth._project_id = None
    yield
    gcp_auth._credentials = None
    gcp_auth._project_id = None


class TestInitializeCredentials:
    """initialize_credentials のテスト."""

    @patch("app.infra.gcp_auth.Request")
    @patch("app.infra.gcp_auth.google_auth_default")
    def test_success(self, mock_default: MagicMock, mock_request: MagicMock) -> None:
        """ADC認証成功時に (credentials, project_id) を返す."""
        mock_creds = MagicMock()
        mock_default.return_value = (mock_creds, "my-project")

        credentials, project_id = gcp_auth.initialize_credentials()

        assert credentials is mock_creds
        assert project_id == "my-project"
        mock_default.assert_called_once_with(
            scopes=["https://www.googleapis.com/auth/cloud-platform"]
        )
        mock_creds.refresh.assert_called_once()

    @patch("app.infra.gcp_auth.Request")
    @patch("app.infra.gcp_auth.google_auth_default")
    def test_raises_when_no_credentials(
        self, mock_default: MagicMock, mock_request: MagicMock
    ) -> None:
        """認証情報が見つからない場合 DefaultCredentialsError を送出する."""
        mock_default.side_effect = DefaultCredentialsError("not found")

        with pytest.raises(DefaultCredentialsError):
            gcp_auth.initialize_credentials()


class TestGetCredentials:
    """get_credentials のテスト."""

    def test_returns_none_before_init(self) -> None:
        """初期化前は None を返す."""
        assert gcp_auth.get_credentials() is None

    @patch("app.infra.gcp_auth.Request")
    @patch("app.infra.gcp_auth.google_auth_default")
    def test_returns_credentials_after_init(
        self, mock_default: MagicMock, mock_request: MagicMock
    ) -> None:
        """初期化後は credentials を返す."""
        mock_creds = MagicMock()
        mock_default.return_value = (mock_creds, "my-project")

        gcp_auth.initialize_credentials()

        assert gcp_auth.get_credentials() is mock_creds


class TestGetProjectId:
    """get_project_id のテスト."""

    def test_returns_none_before_init(self) -> None:
        """初期化前は None を返す."""
        assert gcp_auth.get_project_id() is None

    @patch("app.infra.gcp_auth.Request")
    @patch("app.infra.gcp_auth.google_auth_default")
    def test_returns_project_id_after_init(
        self, mock_default: MagicMock, mock_request: MagicMock
    ) -> None:
        """初期化後は project_id を返す."""
        mock_creds = MagicMock()
        mock_default.return_value = (mock_creds, "my-project")

        gcp_auth.initialize_credentials()

        assert gcp_auth.get_project_id() == "my-project"
