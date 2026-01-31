"""Firebase 初期化モジュールのユニットテスト。"""

from __future__ import annotations

from pathlib import Path
from typing import Generator
from unittest.mock import MagicMock, patch

import pytest

from app.infra import firebase


@pytest.fixture(autouse=True)
def _reset_firebase_state() -> Generator[None, None, None]:
    """各テスト前に Firebase のグローバル状態をリセット。"""
    firebase._app = None
    with patch.object(firebase.firebase_admin, "_apps", {}):
        yield


class TestInitializeFirebase:
    """initialize_firebase のテスト。"""

    def test_emulator_mode_initializes_without_credentials(self) -> None:
        with (
            patch.dict(
                "os.environ",
                {
                    "FIREBASE_AUTH_EMULATOR_HOST": "localhost:9099",
                    "GCP_PROJECT_ID": "test-project",
                },
                clear=False,
            ),
            patch.object(
                firebase.firebase_admin,
                "initialize_app",
                return_value=MagicMock(),
            ) as mock_init,
            patch.object(firebase.firebase_admin, "_apps", {}),
        ):
            app = firebase.initialize_firebase()

            mock_init.assert_called_once_with(
                credential=None,
                options={"projectId": "test-project"},
            )
            assert app is not None

    def test_service_account_json_env(self) -> None:
        sa_json = '{"type": "service_account", "project_id": "test"}'
        mock_cred = MagicMock()
        with (
            patch.dict(
                "os.environ",
                {
                    "FIREBASE_SERVICE_ACCOUNT": sa_json,
                    "FIREBASE_AUTH_EMULATOR_HOST": "",
                    "GCP_PROJECT_ID": "test-project",
                },
                clear=False,
            ),
            patch.object(
                firebase.credentials,
                "Certificate",
                return_value=mock_cred,
            ) as mock_cert,
            patch.object(
                firebase.firebase_admin,
                "initialize_app",
                return_value=MagicMock(),
            ) as mock_init,
            patch.object(firebase.firebase_admin, "_apps", {}),
        ):
            firebase.initialize_firebase()

            mock_cert.assert_called_once_with(
                {"type": "service_account", "project_id": "test"}
            )
            mock_init.assert_called_once_with(
                credential=mock_cred,
                options={"projectId": "test-project"},
            )

    def test_credentials_file_path(self, tmp_path: Path) -> None:
        key_file = tmp_path / "sa-key.json"
        key_file.write_text("{}")
        mock_cred = MagicMock()
        with (
            patch.dict(
                "os.environ",
                {
                    "GOOGLE_APPLICATION_CREDENTIALS": str(key_file),
                    "FIREBASE_SERVICE_ACCOUNT": "",
                    "FIREBASE_AUTH_EMULATOR_HOST": "",
                    "GCP_PROJECT_ID": "prod-project",
                },
                clear=False,
            ),
            patch.object(
                firebase.credentials,
                "Certificate",
                return_value=mock_cred,
            ) as mock_cert,
            patch.object(
                firebase.firebase_admin,
                "initialize_app",
                return_value=MagicMock(),
            ),
            patch.object(firebase.firebase_admin, "_apps", {}),
        ):
            firebase.initialize_firebase()

            mock_cert.assert_called_once_with(str(key_file))

    def test_adc_fallback(self) -> None:
        mock_cred = MagicMock()
        with (
            patch.dict(
                "os.environ",
                {
                    "GOOGLE_APPLICATION_CREDENTIALS": "",
                    "FIREBASE_SERVICE_ACCOUNT": "",
                    "FIREBASE_AUTH_EMULATOR_HOST": "",
                    "GCP_PROJECT_ID": "prod-project",
                },
                clear=False,
            ),
            patch.object(
                firebase.credentials,
                "ApplicationDefault",
                return_value=mock_cred,
            ) as mock_adc,
            patch.object(
                firebase.firebase_admin,
                "initialize_app",
                return_value=MagicMock(),
            ) as mock_init,
            patch.object(firebase.firebase_admin, "_apps", {}),
        ):
            firebase.initialize_firebase()

            mock_adc.assert_called_once()
            mock_init.assert_called_once_with(
                credential=mock_cred,
                options={"projectId": "prod-project"},
            )

    def test_returns_cached_app_on_second_call(self) -> None:
        mock_app = MagicMock()
        with (
            patch.dict(
                "os.environ",
                {
                    "FIREBASE_AUTH_EMULATOR_HOST": "localhost:9099",
                    "GCP_PROJECT_ID": "test-project",
                },
                clear=False,
            ),
            patch.object(
                firebase.firebase_admin,
                "initialize_app",
                return_value=mock_app,
            ) as mock_init,
            patch.object(firebase.firebase_admin, "_apps", {}),
        ):
            first = firebase.initialize_firebase()
            second = firebase.initialize_firebase()

            assert first is second
            assert mock_init.call_count == 1


class TestGetFirebaseApp:
    """get_firebase_app のテスト。"""

    def test_initializes_if_not_yet(self) -> None:
        mock_app = MagicMock()
        with (
            patch.dict(
                "os.environ",
                {
                    "FIREBASE_AUTH_EMULATOR_HOST": "localhost:9099",
                    "GCP_PROJECT_ID": "test-project",
                },
                clear=False,
            ),
            patch.object(
                firebase.firebase_admin,
                "initialize_app",
                return_value=mock_app,
            ),
            patch.object(firebase.firebase_admin, "_apps", {}),
        ):
            result = firebase.get_firebase_app()
            assert result is mock_app

    def test_returns_existing_app(self) -> None:
        mock_app = MagicMock()
        firebase._app = mock_app
        result = firebase.get_firebase_app()
        assert result is mock_app


class TestVerifyIdToken:
    """verify_id_token のテスト。"""

    def test_valid_token(self) -> None:
        expected = {"uid": "user123", "email": "test@example.com"}
        with (
            patch.dict(
                "os.environ",
                {
                    "FIREBASE_AUTH_EMULATOR_HOST": "localhost:9099",
                    "GCP_PROJECT_ID": "test-project",
                },
                clear=False,
            ),
            patch.object(
                firebase.firebase_admin,
                "initialize_app",
                return_value=MagicMock(),
            ),
            patch.object(firebase.firebase_admin, "_apps", {}),
            patch.object(firebase.auth, "verify_id_token", return_value=expected),
        ):
            result = firebase.verify_id_token("valid-token")
            assert result == expected

    def test_invalid_token_raises(self) -> None:
        with (
            patch.dict(
                "os.environ",
                {
                    "FIREBASE_AUTH_EMULATOR_HOST": "localhost:9099",
                    "GCP_PROJECT_ID": "test-project",
                },
                clear=False,
            ),
            patch.object(
                firebase.firebase_admin,
                "initialize_app",
                return_value=MagicMock(),
            ),
            patch.object(firebase.firebase_admin, "_apps", {}),
            patch.object(
                firebase.auth,
                "verify_id_token",
                side_effect=Exception("Invalid token"),
            ),
        ):
            with pytest.raises(Exception, match="Invalid token"):
                firebase.verify_id_token("bad-token")
