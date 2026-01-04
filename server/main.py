"""Uvicorn entrypoint.

AGENTS.md の想定通り `uv run uvicorn main:app --reload` で起動できるように、
実体は `app.main` 側に寄せています。
"""

from app.main import app  # noqa: F401
