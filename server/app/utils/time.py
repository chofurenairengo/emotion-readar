from __future__ import annotations

from datetime import datetime, timezone

JST = timezone.utc  # TODO: replace with proper JST if needed


def utc_now() -> datetime:
    return datetime.now(timezone.utc)
