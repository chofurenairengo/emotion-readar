from __future__ import annotations

from typing import Any

from pydantic import BaseModel


class ChatRequest(BaseModel):
    text: str
    non_verbal: dict[str, Any] = {}
