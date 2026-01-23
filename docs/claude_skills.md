プロジェクトに配備されているスキルとルールを整理します。

ルール（.claude/rules/）
ファイル	内容
agents.md	エージェントオーケストレーション（planner, architect, tdd-guide等の使い分け）
coding-style.md	イミュータビリティ必須、ファイル分割、エラーハンドリング、入力バリデーション
git-workflow.md	コミットメッセージ形式（conventional commits）、PR・機能実装ワークフロー
hooks.md	PreToolUse/PostToolUse/Stopフック設定（Prettier、tsc、console.log警告等）
patterns.md	API Response、Custom Hooks、Repositoryパターン
performance.md	モデル選択戦略、コンテキストウィンドウ管理
security.md	コミット前チェックリスト、シークレット管理
testing.md	カバレッジ80%必須、TDDワークフロー
スキル（Skill tool で呼び出し可能）
スキル	用途
/plan	要件整理・リスク評価・実装計画作成
/tdd	テスト駆動開発ワークフロー強制
/code-review	コードレビュー
/security-review	セキュリティレビュー
/build-fix	ビルドエラー修正
/e2e	Playwright E2Eテスト生成・実行
/refactor-clean	デッドコード削除・整理
/test-coverage	テストカバレッジ確認
/verify	検証コマンド
/checkpoint	チェックポイント作成
/orchestrate	オーケストレーション
/eval	評価コマンド
/learn	セッションから再利用パターン抽出
/update-codemaps	コードマップ更新
/update-docs	ドキュメント更新
/backend-patterns	バックエンドアーキテクチャパターン
/clickhouse-io	ClickHouseパターン
/coding-standards	コーディング標準
/continuous-learning	継続学習パターン抽出
/frontend-patterns	フロントエンド開発パターン
/strategic-compact	コンテキスト圧縮提案
/tdd-workflow	TDDワークフロー
グローバルルール（~/.claude/rules/）
プロジェクトルールと同一内容がグローバルにも設定されており、全プロジェクトで適用されます。

skillsにあるcommandの使い方を教えて
スキルコマンドの使い方
スキルは /コマンド名 の形式でチャットに入力するだけで呼び出せます。引数が必要な場合はスペースに続けて記述します。

基本構文

/スキル名
/スキル名 引数
使用例
コマンド	説明
/plan	現在のタスクの実装計画を作成。ユーザー確認後にコード着手
/tdd	TDDワークフローを強制。テスト先行で実装
/code-review	書いたコードのレビューを実行
/security-review	セキュリティ脆弱性の検出・修正提案
/build-fix	ビルドエラーの解析・修正
/e2e	Playwright E2Eテストの生成・実行
/refactor-clean	デッドコード検出・削除
/test-coverage	テストカバレッジの確認
/verify	実装の検証
/checkpoint	チェックポイント作成
/learn	セッションから再利用パターンを抽出して保存
/update-docs	ドキュメント更新
/update-codemaps	コードマップ更新
引数付きの例

/tdd server/app/services/session_service.py
/e2e "ログインフロー"
/code-review server/app/api/routers/
典型的なワークフロー

1. /plan              ← 実装計画を立てる
2. （ユーザーが確認）
3. /tdd               ← テスト駆動で実装開始
4. /code-review       ← コードレビュー
5. /security-review   ← セキュリティチェック
6. /build-fix         ← ビルドエラーがあれば修正
7. /verify            ← 最終検証
8. /learn             ← パターン抽出・保存
実際に試したいコマンドがあれば、そのまま入力してください。