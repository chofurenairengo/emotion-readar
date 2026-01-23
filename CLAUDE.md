# CLAUDE.md

このファイルは、AIアシスタント（Claude等）がこのリポジトリを理解し、効率的に作業するためのガイドです。

## プロジェクト概要

**Comm-XR（コミュXR）** は、眼鏡型XRデバイスを用いた対面コミュニケーション支援システムです。言語（会話内容）と非言語（表情・視線・声のトーン）の情報をリアルタイムに解析し、ユーザーの対人能力を拡張します。

### 目的
- 会話の行き詰まり解消
- 非言語情報の可視化
- 「コミュニケーションの補助輪」として現実の人間関係を支援

## リポジトリ構成

```
emotion-readar/
├── client/                    # フロントエンドクライアント
│   ├── Mitou/                 # Unity プロジェクト（XR HUD/VFX描画）
│   │   ├── Assets/            # Unityアセット・スクリプト
│   │   ├── Packages/          # Unity パッケージ
│   │   └── ProjectSettings/   # Unity 設定
│   └── android/               # Android ネイティブアプリ（Kotlin）
│       └── app/src/main/      # MediaPipe解析・Unity連携
├── server/                    # FastAPI バックエンド
│   ├── app/                   # アプリケーションコード
│   │   ├── api/               # エンドポイント（routers）
│   │   ├── core/              # インターフェース・設定
│   │   ├── dto/               # リクエスト/レスポンス型
│   │   ├── infra/             # リポジトリ実装
│   │   ├── models/            # ドメインモデル
│   │   └── services/          # ビジネスロジック
│   ├── main.py                # FastAPI エントリーポイント
│   ├── pyproject.toml         # Python プロジェクト設定
│   └── uv.lock                # 依存関係ロックファイル
├── docs/                      # ドキュメント・設計図
├── docker-compose.yml         # ローカル開発環境（API + DynamoDB）
└── .github/                   # CI/CD・テンプレート
```

## 開発環境セットアップ

### バックエンド（Python/FastAPI）

```bash
cd server

# 依存関係インストール（通常）
uv sync

# 開発ツール含む
uv sync --group dev
```

### ローカルサーバー起動

```bash
# 開発サーバー起動（ホットリロード有効）
cd server
uv run uvicorn main:app --reload

# または Docker で起動（API + DynamoDB Local）
docker compose up -d
```

### Docker 操作

```bash
# 起動
docker compose up -d

# 停止
docker compose stop

# ログ確認
docker compose logs -f api
```

## コード品質コマンド

### Lint・フォーマット

```bash
cd server

# Ruff リンター
uv run ruff check .

# Ruff 自動修正
uv run ruff check . --fix

# Black フォーマッター
uv run black .

# Black チェックのみ
uv run black . --check

# Mypy 型チェック
uv run mypy .
```

### テスト

```bash
cd server
uv run pytest
```

### Android

```bash
cd client/android

# デバッグビルド
./gradlew assembleDebug

# ユニットテスト
./gradlew test

# 結合テスト（エミュレータ/実機必須）
./gradlew connectedAndroidTest
```

### Unity

Unity Editor で `client/Mitou` を開いて開発。

## アーキテクチャ

### 3層構成

1. **Edge Layer（Android端末）**: カメラ・マイク入力、MediaPipeによる非言語特徴量抽出
2. **Experience Layer（Unity）**: XR HUD表示、VFX演出、最小限UI
3. **Cloud Intelligence Layer（AWS/FastAPI）**: 音声認識(STT)、LLM推論、状態管理

### サーバーサイドアーキテクチャ（Clean Architecture）

```
app/
├── core/interfaces/     # Layer 1: 抽象インターフェース
├── models/              # Layer 1: ドメインエンティティ
├── dto/                 # Layer 1: リクエスト/レスポンス型
├── services/            # Layer 2: ユースケース・ビジネスロジック
├── infra/repositories/  # Layer 2: 外部依存の実装
└── api/routers/         # Layer 3: HTTPエンドポイント
```

### データフロー

1. カメラ・マイク・センサー入力
2. Android で非言語特徴量をリアルタイム抽出（画像は即時破棄）
3. 音声をサーバーへ送信 → 音声認識（STT）
4. テキスト + 非言語特徴量を LLM へ入力
5. 返答候補・戦略を Unity にプッシュ表示

## 技術スタック

| 層 | 技術 |
|---|---|
| サーバー | Python 3.14, FastAPI, uvicorn, DynamoDB |
| パッケージ管理 | uv |
| Android | Kotlin, Jetpack Compose, MediaPipe |
| Unity | C#, AR Foundation |
| LLM | Gemini API |
| インフラ | AWS (ECS Fargate, DynamoDB, Cognito) |

## コーディング規約

### Python

- **フォーマッター**: Black（line-length: 88）
- **リンター**: Ruff
- **命名規則**:
  - 関数・変数: `snake_case`
  - クラス: `PascalCase`
- **型ヒント**: 必須（mypy でチェック）

### Kotlin / C#

- IDE 標準フォーマッティング
- 関数・変数: `camelCase`
- クラス・型: `PascalCase`

## API エンドポイント

サーバーは以下のルーターを提供：

| パス | 説明 |
|---|---|
| `/api/health` | ヘルスチェック |
| `/api/sessions` | セッション管理（開始・終了・状態取得） |
| `/api/features` | 非言語特徴量の受信 |
| `/api/realtime` | WebSocket リアルタイム通信 |

### WebSocket メッセージタイプ

- `PING` → `PONG`: 接続確認
- `RESET` → `RESET_ACK`: セッションリセット
- `ERROR_REPORT` → `ERROR_ACK`: エラー報告

## Git・PR ガイドライン

### コミットメッセージ

- 短く簡潔に（1行）
- 任意でプレフィックス使用: `init:`, `fix:`, `feat:`, `docs:` など

### Pull Request

PRには以下を含める：

```markdown
## issueの番号
close #XX

## やったこと
実装内容の説明（UIの変更はスクリーンショット添付）

## 動作確認
テスト方法と結果

## その他
レビュワーへの参考情報
```

### Issue テンプレート

- **概要**: 目的と内容を1行で
- **期待する仕様**: 期待される動作
- **関連issue&link**: 関連情報

## CI/CD

GitHub Actions で以下を自動実行（`main` ブランチへの push/PR時）：

1. `uv sync` - 依存関係インストール
2. `ruff check .` - リントチェック
3. `black . --check` - フォーマットチェック
4. `mypy .` - 型チェック

## 環境変数・シークレット

- `.env` ファイルでローカル設定（Git 管理外）
- 本番は AWS Secrets Manager 使用
- **絶対にコミットしないもの**: APIキー、認証情報、`.env` ファイル

### Docker 環境変数

```
AWS_REGION=ap-northeast-1
AWS_ACCESS_KEY_ID=local
AWS_SECRET_ACCESS_KEY=local
DYNAMODB_ENDPOINT=http://dynamodb-local:8000
DYNAMODB_TABLE_PREFIX=dev_
```

## ローカルサービス

Docker Compose で以下が起動：

| サービス | ポート | 説明 |
|---|---|---|
| api | 8000 | FastAPI サーバー |
| dynamodb-local | 8001 | DynamoDB Local |
| dynamodb-admin | 8002 | DynamoDB 管理UI |

## プライバシー方針

- 顔画像・映像はクラウドに送信しない
- 生音声は保存しない（同意時のみ例外）
- 送信データは特徴量とテキストのみ
- 通信は TLS 暗号化

## 注意事項（AIアシスタント向け）

1. **日本語で回答してください** - このプロジェクトは日本語がメイン言語です
2. **Clean Architecture を遵守** - 依存関係の方向を守る
3. **プライバシー重視** - 個人情報・生データの取り扱いに注意
4. **既存ファイルの編集を優先** - 新規ファイル作成は最小限に
5. **コード変更前にリント・フォーマット確認** - `ruff` と `black` を使用
6. **型ヒントを必ず追加** - Python コードには型アノテーション必須

## 関連ドキュメント

- `README.md` - プロジェクト概要・設計仕様書
- `AGENTS.md` - AIエージェント向け詳細ガイドライン
- `docs/` - アーキテクチャ図・技術資料
