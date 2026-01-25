# GitHub Copilot スキルリファレンス

プロジェクトで利用可能な Copilot コマンドとワークフローを整理します。

---

## コマンド一覧 (.github/prompts/)

| コマンド                           | 説明                                         |
| ---------------------------------- | -------------------------------------------- |
| `/execute-plan`                    | PLAN.md の実装計画を実行                     |
| `/execute-review`                  | CODEREVIEW.md の問題を修正                   |
| `/run-claude-review`               | Claude CLI でレビューを自動実行              |
| `/execute-plan-code-review-file`   | 実装 → Claude レビュー（統合ワークフロー）   |
| `/execute-review-code-review-file` | 修正 → Claude 再レビュー（統合ワークフロー） |

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
1. **CRITICAL** - 最優先（セキュリティ問題）
2. **HIGH** - 必須（品質問題）
3. **MEDIUM** - 推奨（ベストプラクティス）
4. **LOW** - 可能なら（スタイル問題）

**使い方:**
```
@workspace /execute-review
```

---

### `/run-claude-review`

Claude CLI を使用してコードレビューを自動実行します。

**実行されるコマンド:**
```bash
claude --allowedTools "Write" -p "/code-review-file"
```

**動作:**
1. `git diff` で未コミットの変更を検出
2. 各ファイルをセキュリティ・品質観点でレビュー
3. 問題を CRITICAL / HIGH / MEDIUM / LOW に分類
4. 結果を `CODEREVIEW.md` に自動保存

**使い方:**
```
@workspace /run-claude-review
```

**前提条件:**
- Claude CLI がインストールされていること（`claude --version` で確認）

---

### `/execute-plan-code-review-file`

実装とレビューを連続で実行する統合ワークフローです。

**ステップ:**
1. PLAN.md を読み込んで実装を実行
2. Claude CLI でコードレビューを自動実行
3. 結果を `CODEREVIEW.md` に保存

**使い方:**
```
@workspace /execute-plan-code-review-file
```

---

### `/execute-review-code-review-file`

修正と再レビューを連続で実行する統合ワークフローです。

**ステップ:**
1. CODEREVIEW.md を読み込んで問題を修正
2. Claude CLI で再レビューを自動実行
3. 結果を `CODEREVIEW.md` に保存（上書き）

**使い方:**
```
@workspace /execute-review-code-review-file
```

---

## 推奨ワークフロー

### 新機能実装

```
1. Claude: /plan-file           ← PLAN.md を作成
2. Copilot: /execute-plan       ← PLAN.md から実装
3. Copilot: /run-claude-review  ← レビュー → CODEREVIEW.md
4. Copilot: /execute-review     ← 問題を修正
5. Copilot: /run-claude-review  ← 修正を確認
6. コミット
```

### クイックフィックス

```
1. Copilot: /run-claude-review  ← 変更をレビュー
2. Copilot: /execute-review     ← 問題を修正
3. 問題がなくなるまで繰り返し
4. コミット
```

### 統合ワークフロー（自動化）

```
1. Claude: /plan-file                       ← PLAN.md を作成
2. Copilot: /execute-plan-code-review-file  ← 実装 + レビュー
3. Copilot: /execute-review-code-review-file ← 修正 + 再レビュー（必要に応じて繰り返し）
4. コミット
```

---

## 関連ファイル

| ファイル                          | 説明                               |
| --------------------------------- | ---------------------------------- |
| `PLAN.md`                         | 実装計画（Claude Code が生成）     |
| `CODEREVIEW.md`                   | レビュー結果（Claude Code が生成） |
| `.github/copilot-instructions.md` | Copilot の基本設定                 |
| `docs/claude_skills.md`           | Claude Code コマンドリファレンス   |
| `AGENTS.md`                       | プロジェクト仕様書                 |

---

## トラブルシューティング

### Claude CLI が見つからない

```bash
claude --version
```

インストールされていない場合は、Claude Code 拡張機能をインストールしてください。

### PLAN.md / CODEREVIEW.md が存在しない

- `/execute-plan` を実行する前に Claude で `/plan-file` を実行
- `/execute-review` を実行する前に `/run-claude-review` を実行

### レビューで変更が検出されない

未コミットの変更がない場合、レビュー対象がありません。

```bash
git status
```
