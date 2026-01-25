# GitHub Copilot Instructions

このリポジトリでの Copilot の動作に関する指示です。

---

## 基本ルール

1. 日本語で回答
2. クリーンアーキテクチャに従う
3. AGENTS.md の仕様書に従う

---

## コマンド一覧 (.github/prompts/)

### 実行コマンド

| コマンド | 説明 |
|----------|------|
| `/execute-plan` | PLAN.md の実装計画を実行 |
| `/execute-review` | CODEREVIEW.md の問題を修正 |
| `/run-claude-review` | Claude CLI でレビューを自動実行 |

### 統合ワークフロー

| コマンド | 説明 |
|----------|------|
| `/execute-plan-code-review-file` | 実装 → レビュー（Copilot + Claude） |
| `/execute-review-code-review-file` | 修正 → 再レビュー（Copilot + Claude） |

---

## コマンド詳細

### `/execute-plan`

PLAN.md に記載された実装計画を実行します。

**重要なルール:**
- PLAN.md に記載された内容**のみ**実装する
- 追加機能や改善は実装しない
- リファクタリングは行わない
- 不明点がある場合は確認する

**使い方:**
```
@workspace /execute-plan
```

---

### `/execute-review`

CODEREVIEW.md に記載された問題を修正します。

**重要なルール:**
- CODEREVIEW.md に記載された問題**のみ**修正する
- 「気づいた」問題は修正しない
- 追加の改善は行わない
- 不明点がある場合は確認する

**優先順位:**
1. CRITICAL - 最優先（セキュリティ問題）
2. HIGH - 必須（品質問題）
3. MEDIUM - 推奨（ベストプラクティス）
4. LOW - 可能なら（スタイル問題）

**使い方:**
```
@workspace /execute-review
```

---

### `/execute-plan-code-review-file`

実装とレビューを連続で実行するワークフローです。

**ステップ:**
1. Copilot: `/execute-plan` で実装
2. Claude Code: `/code-review-file` でレビュー

---

### `/execute-review-code-review-file`

修正と再レビューを連続で実行するワークフローです。

**ステップ:**
1. Copilot: `/execute-review` で修正
2. Claude Code: `/code-review-file` で再レビュー

---

## 推奨ワークフロー

### 新機能実装

```
1. Claude: /plan-file           ← PLAN.md を作成
2. Copilot: /execute-plan       ← PLAN.md から実装
3. Claude: /code-review-file    ← レビュー → CODEREVIEW.md
4. Copilot: /execute-review     ← 問題を修正
5. Claude: /code-review-file    ← 修正を確認
6. コミット
```

### クイックフィックス

```
1. Claude: /code-review-file    ← 変更をレビュー
2. Copilot: /execute-review     ← 問題を修正
3. 問題がなくなるまで繰り返し
4. コミット
```

---

## 関連ファイル

- `PLAN.md` - 実装計画（Claude Code が生成）
- `CODEREVIEW.md` - レビュー結果（Claude Code が生成）
- `AGENTS.md` - プロジェクト仕様書
- `docs/copilot-skills.md` - Copilot コマンドリファレンス
- `docs/claude-skills.md` - Claude Code コマンドリファレンス
