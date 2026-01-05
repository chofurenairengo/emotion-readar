from __future__ import annotations

import threading
import uuid
from datetime import datetime, timezone

from app.dto.feature import FeatureRequest
from app.models.feature_log import FeatureLog
from app.services.session_service import SessionService

FEATURE_STATUS_ACCEPTED = "accepted"

_feature_logs: list[FeatureLog] = []
_lock = threading.Lock()


def record_features(
    payload: FeatureRequest, session_service: SessionService
) -> FeatureLog:
    if (
        payload.session_id is not None
        and session_service.get_session(payload.session_id) is None
    ):
        raise LookupError("session_not_found")

    log = FeatureLog(
        id=str(uuid.uuid4()),
        session_id=payload.session_id,
        received_at=datetime.now(timezone.utc),
        client_timestamp=payload.timestamp,
        facial=payload.facial,
        gaze=payload.gaze,
        voice=payload.voice,
        extras=payload.extras,
    )
    with _lock:
        _feature_logs.append(log)
    return log
