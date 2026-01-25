# Claude Code & Copilot スキルリファレンス

プロジェクトで利用可能なコマンドとワークフローを整理します。

---

## Claude Code コマンド (.claude/commands/)

| コマンド            | 説明                                        |
| ------------------- | ------------------------------------------- |
| `/plan`             | 実装計画を作成（対話形式）                  |
| `/plan-file`        | 実装計画を作成し PLAN.md に保存             |
| `/code-review`      | コードレビューを実行（対話形式）            |
| `/code-review-file` | コードレビューを実行し CODEREVIEW.md に保存 |
| `/tdd`              | テスト駆動開発ワークフロー                  |
| `/build-fix`        | ビルドエラーを修正                          |
| `/e2e`              | Playwright E2Eテストの生成・実行            |
| `/refactor-clean`   | デッドコードを削除                          |
| `/test-coverage`    | テストカバレッジを確認                      |
| `/verify`           | 実装を検証                                  |
| `/checkpoint`       | チェックポイントを作成                      |
| `/orchestrate`      | オーケストレーションコマンド                |
| `/eval`             | 評価コマンド                                |
| `/learn`            | セッションから再利用パターンを抽出          |
| `/update-codemaps`  | コードマップを更新                          |
| `/update-docs`      | ドキュメントを更新                          |

---

## Copilot コマンド (.github/prompts/)

| コマンド             | 説明                                                 |
| -------------------- | ---------------------------------------------------- |
| `/execute-plan`      | PLAN.md を実行（指定された内容のみ実装）             |
| `/execute-review`    | CODEREVIEW.md の問題を修正（記載された問題のみ修正） |
| `/run-claude-review` | Claude CLI でレビューを自動実行                       |

---

## 統合ワークフロー (.github/prompts/)

| ワークフロー                       | ステップ                                                     |
| ---------------------------------- | ------------------------------------------------------------ |
| `/execute-plan-code-review-file`   | 1. Copilot 実装 → 2. Claude CLI `/code-review-file`         |
| `/execute-review-code-review-file` | 1. Copilot 修正 → 2. Claude CLI `/code-review-file`         |

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

### TDD ワークフロー

```
1. Claude: /tdd                 ← テスト先行で開発
2. Claude: /code-review-file    ← レビュー
3. Copilot: /execute-review     ← 問題を修正
4. コミット
```

---

## 使用例

### Claude Code

```
/plan-file WebSocketでリアルタイム通知機能を追加
/code-review-file
/tdd server/app/services/session_service.py
```

### Copilot

```
@workspace /execute-plan
@workspace /execute-review
```

---

## 重要なルール

### Copilot コマンド

- **`/execute-plan`**: PLAN.md に記載された内容のみ実装。それ以外は実装しない。
- **`/execute-review`**: CODEREVIEW.md に記載された問題のみ修正。それ以外は修正しない。

### Claude Code コマンド

- **`/plan-file`**: PLAN.md に保存、チャット出力は最小限
- **`/code-review-file`**: CODEREVIEW.md に保存、チャット出力は最小限