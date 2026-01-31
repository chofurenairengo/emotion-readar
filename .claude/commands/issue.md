---
description: Issue駆動開発フローを開始。Issue分析、仕様確認、実装、レビュー、Issue更新を一貫して実行。
---

# Issue Command

Issueベースの開発フローを自動化するコマンド。

## 使用方法

```
/issue #<issue番号>
/issue <issue番号>
```

## 実行フロー

### Phase 1: Issue取得・分析

1. `gh issue view` でIssue情報取得
2. `issue-analyzer`エージェントで仕様分析
3. `git worktree add -b feature/<番号> <番号>` でworktree作成

### Phase 2: 仕様確認・Issue更新

1. 問題点があればユーザーに質問
2. 回答に基づき `gh issue edit` でIssue本文を更新

### Phase 3: 計画・実装

1. `planner`エージェントで実装計画作成
2. ユーザー確認後、仕様駆動で実装

### Phase 4: コードレビュー・修正

1. `issue-code-reviewer`エージェントで仕様範囲内レビュー
2. 問題があれば修正
3. Issue本文を最終更新

※ PR作成は `/issue-pr #XX` で実行

## 自動認識トリガー

以下の自然言語でもフロー開始:
- 「issue #XX を実装して」
- 「#XX の対応をお願い」
- 「issue XX に基づいて実装」

## 重要な制約

- **仕様のみ実装**: issueに書かれた仕様のみ実装
- **仕様内レビュー**: 仕様範囲内の問題のみ指摘
- **Issue更新**: `gh issue edit`で本文を編集

## 関連エージェント

- `issue-analyzer` - Issue分析
- `planner` - 計画作成
- `issue-code-reviewer` - 仕様範囲内レビュー
