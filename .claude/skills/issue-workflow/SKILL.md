---
name: issue-workflow
description: Issue駆動開発の完全なワークフロー。Issue分析から実装、レビュー、Issue更新まで一貫して実行。/issue コマンドまたは自然言語でissue実装指示があったときに使用。
---

# Issue駆動開発ワークフロー

Issue番号を指定して開発フロー全体を自動化するスキル。

## 起動トリガー

### コマンド
```
/issue #42
/issue 42
```

### 自然言語
- 「issue #42 を実装して」
- 「#42 の対応をお願い」
- 「issue 42 に基づいて実装」

## ワークフローフェーズ

### Phase 1: 初期化
```bash
# Issue情報取得
gh issue view $ISSUE_NO --json number,title,body,labels,state,comments

# worktree作成
git worktree add -b feature/$ISSUE_NO $ISSUE_NO
cd $ISSUE_NO
```

### Phase 2: 分析
`issue-analyzer`エージェントを起動:
- 仕様の曖昧さを検出
- 矛盾を特定
- 不足を洗い出し
- 質問リストを生成

### Phase 3: 仕様確認
質問がある場合:
1. ユーザーに質問を提示
2. 回答を受け取る
3. `gh issue edit $ISSUE_NO --body "$UPDATED_BODY"` でIssue本文を更新

### Phase 4: 計画
`planner`エージェントを起動:
- 実装計画を作成
- リスクを評価
- ユーザー承認を待つ

### Phase 5: 仕様駆動実装
- issueの仕様に基づいて実装
- 仕様に書かれた機能のみを実装
- 過剰な機能追加は禁止

### Phase 6: レビュー
`issue-code-reviewer`エージェントを起動:
- 仕様範囲内で変更をレビュー
- 仕様外の改善提案はしない
- 問題があれば修正

### Phase 7: 最終化
```bash
# Issue本文を最終更新
gh issue edit $ISSUE_NO --body "$FINAL_BODY"

# コミット作成（ユーザー確認後）
git add .
git commit -m "feat: #$ISSUE_NO 実装完了"
```

## 重要な制約

### 実装の制約
- issueに書かれた仕様のみを実装
- 「あったほうが良い」機能を勝手に追加しない
- テスト先行（TDD）ではなく、仕様先行で実装

### レビューの制約
- issueの仕様範囲内の問題のみ指摘
- 仕様外の改善提案はしない
- 他issueで対応すべき問題は指摘しない

### 独立性
- 各issueは他issueに依存しない
- 他issueの成果物を前提としない

## Issue本文更新テンプレート

```markdown
[元のIssue本文はそのまま維持]

---

## 実装記録（自動生成）

### 仕様確認 (YYYY-MM-DD)
| 質問 | 回答 |
|------|------|
| [Q1] | [A1] |

### 実装完了 (YYYY-MM-DD)
- ブランチ: feature/$ISSUE_NO
- 変更ファイル: X個

### 変更ファイル
- `path/to/file1.py` - [概要]
- `path/to/file2.py` - [概要]

### レビュー結果
- 状態: APPROVED
```

## 成功基準

- [ ] 全質問への回答完了（またはスキップ確認）
- [ ] 計画がユーザー承認済み
- [ ] issueの仕様が実装完了
- [ ] レビューで仕様内問題なし
- [ ] Issue本文が更新済み
- [ ] 他issueへの影響なし

## エージェント連携

```
issue-analyzer → planner → 仕様駆動実装 → issue-code-reviewer
     ↓              ↓           ↓                ↓
  [分析]        [計画]       [実装]           [レビュー]
     ↓              ↓           ↓                ↓
  質問生成      承認待ち    仕様のみ実装      仕様内問題検出
     ↓              ↓           ↓                ↓
  Issue更新    Issue更新   Issue更新         Issue更新
```
