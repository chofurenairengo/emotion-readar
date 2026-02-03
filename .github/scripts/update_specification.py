#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    readme_path = repo_root / "README.md"
    spec_path = repo_root / "SPECIFICATION.md"

    if not readme_path.exists():
        raise FileNotFoundError(f"README.md not found at {readme_path}")

    readme_text = readme_path.read_text(encoding="utf-8")
    header = "<!-- Auto-generated from README.md. Do not edit directly. -->\n\n"
    spec_text = header + readme_text

    if spec_path.exists():
        current = spec_path.read_text(encoding="utf-8")
        if current == spec_text:
            return 0

    spec_path.write_text(spec_text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
