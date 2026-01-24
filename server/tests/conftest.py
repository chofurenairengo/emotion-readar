import sys
from pathlib import Path

# Ensure `server/` is on sys.path so tests can import `app.*`.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
