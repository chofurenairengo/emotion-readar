"""SPECIFICATION.md自動更新スクリプト

GitHub APIからプロジェクト情報を取得し、仕様書の動的セクションを更新する。
標準ライブラリのみ使用（外部依存なし）。
"""

import json
import os
import re
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen

SPEC_PATH = "SPECIFICATION.md"
REPO = os.environ.get("REPO", "chofurenairengo/commuxr")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
PR_NUMBER = os.environ.get("PR_NUMBER", "")
PR_TITLE = os.environ.get("PR_TITLE", "")
PR_AUTHOR = os.environ.get("PR_AUTHOR", "")
JST = timezone(timedelta(hours=9))

# 除外するボットアカウント
EXCLUDED_USERS = {"github-actions[bot]", "dependabot[bot]"}


def github_api(endpoint: str, all_pages: bool = False) -> list | dict:
    """GitHub REST API呼び出し（ページネーション対応）"""
    url = f"https://api.github.com/{endpoint}"
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"

    results = []
    while url:
        try:
            req = Request(url, headers=headers)
            with urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))

                if not all_pages:
                    return data

                if isinstance(data, list):
                    results.extend(data)
                else:
                    return data

                # ページネーション: Link ヘッダーから次のページを取得
                link_header = resp.getheader("Link", "")
                url = None
                for link in link_header.split(","):
                    if 'rel="next"' in link:
                        url = link.split(";")[0].strip()[1:-1]
                        break
        except HTTPError as e:
            print(f"API Error: {e.code} {e.reason} for {url}")
            return results if all_pages else {}

    return results


def update_dynamic_sections(content: str, sections: dict[str, str]) -> str:
    """マーカー間のコンテンツを更新"""
    for section_name, new_content in sections.items():
        pattern = rf"(<!-- AUTO:{section_name}:START -->).*?(<!-- AUTO:{section_name}:END -->)"
        replacement = rf"\1\n{new_content}\n\2"
        content = re.sub(pattern, replacement, content, flags=re.DOTALL)
    return content


def classify_pr_changes(pr_number: str) -> tuple[bool, bool]:
    """PRの変更ファイルを分類して仕様書更新/プログラム更新を判定"""
    if not pr_number:
        return False, False

    files = github_api(f"repos/{REPO}/pulls/{pr_number}/files", all_pages=True)
    if not files:
        return False, False

    is_spec_update = False
    is_program_update = False

    spec_patterns = ["SPECIFICATION.md", "CLAUDE.md", "docs/"]
    program_patterns = ["server/", "client/", ".github/", "api/"]

    for file in files:
        filename = file.get("filename", "")
        for pattern in spec_patterns:
            if filename.startswith(pattern) or filename == pattern:
                is_spec_update = True
                break
        for pattern in program_patterns:
            if filename.startswith(pattern):
                is_program_update = True
                break

    return is_spec_update, is_program_update


def build_update_history(content: str) -> str:
    """更新履歴セクション生成（折り畳み、仕様書/プログラム分類）"""
    # 既存の履歴をパース
    spec_updates = []
    program_updates = []

    # 既存エントリを抽出
    spec_pattern = r"<!-- AUTO:UPDATE_HISTORY:START -->.*?<details>\s*<summary>仕様書更新</summary>\s*(.*?)</details>"
    program_pattern = r"<details>\s*<summary>プログラム更新</summary>\s*(.*?)</details>\s*<!-- AUTO:UPDATE_HISTORY:END -->"

    spec_match = re.search(spec_pattern, content, re.DOTALL)
    if spec_match:
        table_content = spec_match.group(1)
        for line in table_content.strip().split("\n"):
            if line.startswith("|") and not line.startswith("| 日付") and not line.startswith("| ---"):
                spec_updates.append(line)

    program_match = re.search(program_pattern, content, re.DOTALL)
    if program_match:
        table_content = program_match.group(1)
        for line in table_content.strip().split("\n"):
            if line.startswith("|") and not line.startswith("| 日付") and not line.startswith("| ---"):
                program_updates.append(line)

    # 現在のPRを追加（重複チェック）
    if PR_NUMBER and PR_TITLE and PR_AUTHOR:
        today = datetime.now(JST).strftime("%Y-%m-%d")
        new_entry = f"| {today} | #{PR_NUMBER} {PR_TITLE} | @{PR_AUTHOR} |"

        is_spec, is_program = classify_pr_changes(PR_NUMBER)

        # PR番号で重複チェック
        pr_marker = f"#{PR_NUMBER} "

        if is_spec:
            if not any(pr_marker in entry for entry in spec_updates):
                spec_updates.insert(0, new_entry)
            else:
                # 既存エントリを更新
                spec_updates = [new_entry if pr_marker in e else e for e in spec_updates]

        if is_program:
            if not any(pr_marker in entry for entry in program_updates):
                program_updates.insert(0, new_entry)
            else:
                program_updates = [new_entry if pr_marker in e else e for e in program_updates]

    # テーブル生成
    spec_table = "| 日付       | 内容                      | 更新者 |\n| ---------- | ------------------------- | ------ |\n"
    spec_table += "\n".join(spec_updates) if spec_updates else "| - | - | - |"

    program_table = "| 日付       | 内容                         | 更新者 |\n| ---------- | ---------------------------- | ------ |\n"
    program_table += "\n".join(program_updates) if program_updates else "| - | - | - |"

    return f"""<details>
<summary>仕様書更新</summary>

{spec_table}

</details>

<details>
<summary>プログラム更新</summary>

{program_table}

</details>"""


def build_developers() -> str:
    """開発者一覧生成（contributorsから）"""
    contributors = github_api(f"repos/{REPO}/contributors", all_pages=True)
    if not contributors:
        return "| 開発者 |\n| ------ |\n| - |"

    rows = ["| 開発者 |", "| ------ |"]
    for contributor in contributors:
        login = contributor.get("login", "")
        if login and login not in EXCLUDED_USERS:
            rows.append(f"| {login} |")

    return "\n".join(rows) if len(rows) > 2 else "| 開発者 |\n| ------ |\n| - |"


def build_closed_issues() -> str:
    """過去のIssue一覧生成（タイトルのみ）"""
    issues = github_api(f"repos/{REPO}/issues?state=closed&per_page=100", all_pages=True)
    if not issues:
        return "過去のIssueはありません。"

    lines = []
    for issue in issues:
        # PRを除外
        if "pull_request" in issue:
            continue
        number = issue.get("number", "")
        title = issue.get("title", "")
        lines.append(f"- #{number} {title}")

    return "\n".join(lines) if lines else "過去のIssueはありません。"


def build_open_issues() -> str:
    """現在のIssue一覧生成（assignee+概要付き）"""
    issues = github_api(f"repos/{REPO}/issues?state=open&per_page=100", all_pages=True)
    if not issues:
        return "| #    | タイトル | 担当 | 概要 |\n| ---- | -------- | ---- | ---- |\n| - | - | - | - |"

    rows = [
        "| #    | タイトル               | 担当 | 概要                                                 |",
        "| ---- | ---------------------- | ---- | ---------------------------------------------------- |",
    ]

    for issue in issues:
        # PRを除外
        if "pull_request" in issue:
            continue
        number = issue.get("number", "")
        # パイプ文字をエスケープしてテーブル崩れを防止
        title = issue.get("title", "")[:30].replace("|", "\\|")
        assignees = issue.get("assignees", [])
        assignee = ", ".join([f"@{a['login']}" for a in assignees]) if assignees else "-"
        body = issue.get("body", "") or ""
        # 最初の行を概要として使用（最大50文字）、パイプ文字をエスケープ
        summary = body.split("\n")[0][:50].replace("|", "\\|") if body else "-"
        rows.append(f"| #{number} | {title} | {assignee} | {summary} |")

    return "\n".join(rows)


def build_roadmap() -> str:
    """開発予定生成（open Epicから）"""
    issues = github_api(f"repos/{REPO}/issues?state=open&labels=Epic&per_page=100", all_pages=True)

    lines = ["### Epicベースの開発予定\n"]

    if issues:
        for issue in issues:
            if "pull_request" in issue:
                continue
            number = issue.get("number", "")
            title = issue.get("title", "")
            lines.append(f"- #{number} {title}")
    else:
        lines.append("現在オープンなEpicはありません。")

    lines.append("\n### 将来的な拡張\n")
    lines.append("| 拡張 | 説明 |")
    lines.append("|------|------|")
    lines.append("| 話者分離・方向推定の高度化 | DOA精度向上 |")
    lines.append("| マルチユーザー対応 | 複数セッション同時処理 |")
    lines.append("| 成長指標の長期可視化 | コミュニケーション能力の成長追跡 |")
    lines.append("| オフラインモード | 限定機能でのオフライン動作 |")
    lines.append("| ドメイン特化チューニング | LoRAによる恋愛・親睦シーン特化 |")
    lines.append("| 自己解析 | 内カメラによる自分の振る舞いフィードバック |")

    return "\n".join(lines)


def generate_base_template() -> str:
    """SPECIFICATION.mdが存在しない場合のテンプレート生成"""
    return """# Emotion ReadAR（ERA）ソフトウェア仕様書

**文書バージョン**: 1.0
**作成日**: 2026-01-29
**プロジェクト名**: Comm-XR / Emotion ReadAR（E.R.A）
**リポジトリ**: https://github.com/chofurenairengo/emotion-readar

---

## 目次

1. [プロジェクト概要](#1-プロジェクト概要)
2. [解決する課題](#2-解決する課題)
3. [システムアーキテクチャ](#3-システムアーキテクチャ)
4. [技術スタック](#4-技術スタック)
5. [クライアントサイド仕様](#5-クライアントサイド仕様)
6. [サーバーサイド仕様](#6-サーバーサイド仕様)
7. [API仕様](#7-api仕様)
8. [データモデル・DTO定義](#8-データモデルdto定義)
9. [サービス層仕様](#9-サービス層仕様)
10. [インフラストラクチャ](#10-インフラストラクチャ)
11. [セキュリティ仕様](#11-セキュリティ仕様)
12. [プライバシー方針](#12-プライバシー方針)
13. [テスト仕様](#13-テスト仕様)
14. [CI/CD](#14-cicd)
15. [非機能要件](#15-非機能要件)
16. [開発環境](#16-開発環境)
17. [今後の拡張計画](#17-今後の拡張計画)
18. [非対象（Non-Goals）](#18-非対象non-goals)
19. [更新履歴](#19-更新履歴)
20. [開発者](#20-開発者)
21. [過去のIssue](#21-過去のissue)
22. [現在のIssue](#22-現在のissue)
23. [開発予定](#23-開発予定)

---

（静的コンテンツはここに記載）

---

## 19. 更新履歴

<!-- AUTO:UPDATE_HISTORY:START -->
<!-- AUTO:UPDATE_HISTORY:END -->

## 20. 開発者

<!-- AUTO:DEVELOPERS:START -->
<!-- AUTO:DEVELOPERS:END -->

## 21. 過去のIssue

<!-- AUTO:CLOSED_ISSUES:START -->
<!-- AUTO:CLOSED_ISSUES:END -->

## 22. 現在のIssue

<!-- AUTO:OPEN_ISSUES:START -->
<!-- AUTO:OPEN_ISSUES:END -->

## 23. 開発予定

<!-- AUTO:ROADMAP:START -->
<!-- AUTO:ROADMAP:END -->
"""


def main():
    """エントリーポイント"""
    print("=== SPECIFICATION.md 自動更新 ===")
    print(f"リポジトリ: {REPO}")
    print(f"トリガーPR: #{PR_NUMBER} - {PR_TITLE} by @{PR_AUTHOR}")

    # ファイル読み込み or テンプレート生成
    if os.path.exists(SPEC_PATH):
        with open(SPEC_PATH, "r", encoding="utf-8") as f:
            content = f.read()
        print("既存のSPECIFICATION.mdを読み込みました")
    else:
        content = generate_base_template()
        print("SPECIFICATION.mdが存在しないため、テンプレートを生成しました")

    # 動的セクションの生成
    sections = {
        "UPDATE_HISTORY": build_update_history(content),
        "DEVELOPERS": build_developers(),
        "CLOSED_ISSUES": build_closed_issues(),
        "OPEN_ISSUES": build_open_issues(),
        "ROADMAP": build_roadmap(),
    }

    # 更新
    updated_content = update_dynamic_sections(content, sections)

    # 書き込み
    with open(SPEC_PATH, "w", encoding="utf-8") as f:
        f.write(updated_content)

    # サマリー出力
    updated_sections = [name for name in sections.keys()]
    print("\n=== 更新完了 ===")
    print(f"ファイル: {SPEC_PATH}")
    print(f"更新セクション数: {len(updated_sections)}")
    print(f"セクション: {', '.join(updated_sections)}")


if __name__ == "__main__":
    main()
