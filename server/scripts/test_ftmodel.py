#!/usr/bin/env python
"""FTモデルテスト用CLIスクリプト.

Usage:
    uv run python scripts/test_ftmodel.py "プロンプト"
    uv run python scripts/test_ftmodel.py -i  # インタラクティブモード
"""

from __future__ import annotations

import argparse
import asyncio
import os
import sys
from pathlib import Path

# appモジュールをインポートするためにパスを追加
server_dir = Path(__file__).parent.parent
sys.path.insert(0, str(server_dir))

# .envからGOOGLE_APPLICATION_CREDENTIALSを読み込む
env_file = server_dir.parent / ".env"
if env_file.exists():
    with open(env_file, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, _, value = line.partition("=")
                key = key.strip()
                value = value.strip()
                if key == "GOOGLE_APPLICATION_CREDENTIALS" and value:
                    os.environ[key] = value

from langchain_core.messages import HumanMessage

from app.infra.external.gemini_client import LLMClientFactory

# Windows用UTF-8出力設定
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[union-attr]


async def send_prompt(prompt: str) -> str:
    """プロンプトをFTモデルに送信."""
    client = LLMClientFactory.create_ft_client()
    response = await client.ainvoke([HumanMessage(content=prompt)])
    return str(response.content)


async def interactive_mode() -> None:
    """インタラクティブモード."""
    print("FTモデルテスト（'quit'で終了）")
    print("-" * 40)

    while True:
        try:
            prompt = input("\nプロンプト: ").strip()
            if prompt.lower() in ("quit", "exit", "q"):
                break
            if not prompt:
                continue

            response = await send_prompt(prompt)
            print(f"\n{response}")

        except KeyboardInterrupt:
            break

    print("\n終了")


async def main() -> None:
    """メイン関数."""
    parser = argparse.ArgumentParser(description="FTモデルテストCLI")
    parser.add_argument("prompt", nargs="?", help="送信するプロンプト")
    parser.add_argument("-i", "--interactive", action="store_true", help="インタラクティブモード")

    args = parser.parse_args()

    if args.interactive:
        await interactive_mode()
    elif args.prompt:
        response = await send_prompt(args.prompt)
        print(response)
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    # Windows対応
    if sys.platform == "win32":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

    asyncio.run(main())
