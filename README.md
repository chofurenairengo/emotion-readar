# Comm-XR Design Document

画像認識・音声認識・LLM 統合 XR システム

## 目的

対面コミュニケーションの非言語情報（表情・視線・声のトーン）と言語情報（会話内容）を
リアルタイムに解析・可視化し、ユーザーの対人能力を拡張する。

設計ゴール:

- 低遅延でのリアルタイムフィードバック
- プライバシー配慮（顔画像・生音声の外部送信を最小化）
- 拡張可能なアーキテクチャ（将来的なモデル変更・精度向上）
- MVP から本番まで耐える構成

## 全体構成（3 層）

- Edge Layer（端末）: 感覚入力と即時解析
- Experience Layer（Unity）: 表示・UX・XR 表現
- Cloud Intelligence Layer（AWS）: 音声認識・LLM 推論・状態管理

## データフロー概要

- カメラ・マイク・センサー入力
- Android で非言語特徴量をリアルタイム抽出
- Unity で HUD / VFX 表示
- 音声をサーバーへ送信 → 音声認識（STT）
- テキスト + 非言語特徴量を LLM へ入力
- 返答候補・戦略を Unity にプッシュ表示

## Edge Layer（Android）

技術選定:

- Kotlin（MediaPipe 公式対応・低レイテンシ・Unity 連携が容易）

画像認識（MediaPipe）:

- 顔検出 / ランドマーク推定（最大 468 点）
- 表情（Blendshape）・視線・頭部姿勢
- 人物追跡（ID 維持）
- 出力は数値特徴量のみ（生画像は即時破棄）

音声前処理:

- VAD（発話開始/終了）
- 音量・ピッチ・話速（推定）
- STT は行わない

## Experience Layer（Unity）

役割:

- XR 描画・HUD 表示
- 空気の可視化（粒子・色・揺らぎ）
- 最小限の UI 操作

扱うデータ:

- Android 由来の非言語特徴量
- サーバーからの返答候補・戦略タグ
  ※ Unity は解析せず、意味付けと可視化に専念。

## Cloud Intelligence Layer（AWS）

インフラ:

- ALB / ECS Fargate / DynamoDB / S3（任意）
- Cognito / CloudWatch / Secrets Manager

API Server:

- FastAPI / Docker / WebSocket + HTTP

音声認識（STT）:

- サーバー処理
- Amazon Transcribe（Streaming / 非 Streaming）
- 将来的に Whisper 系へ差し替え可能

LLM:

- 入力: STT テキスト / 非言語特徴量 / 会話コンテキスト
- 出力: 返答候補（2〜3）/ 会話戦略タグ / 注意喚起（任意）

## データ・プライバシー方針

- 顔画像・映像はクラウドに送信しない
- 生音声は保存しない（同意時のみ例外）
- 送信データは特徴量とテキストのみ
- 通信は TLS / 秘密情報は Secrets Manager

## リポジトリ構成（モノレポ）

comm-xr/
├─ client/
│ ├─ unity/
│ └─ android-native/
├─ server/
│ ├─ api/
│ └─ worker/
├─ infra/
├─ docs/
└─ docker-compose.yml

## 非対象（Non-Goals）

- 端末での高精度 STT
- 顔画像の長期保存
- 感情の断定（「怒っている」と断言しない）
- フル自動会話（人が話す前提）

## 将来拡張

- 話者分離・方向推定の高度化
- マルチユーザー対応
- 成長指標の長期可視化
- オフラインモード（限定機能）

## Docs

- アーキテクチャ図: docs/architecture.md

comm-xr/
├─ README.md
├─ .gitignore
├─ .gitattributes # 改行/Unity 差分対策
├─ docker-compose.yml # ローカル統合起動（api + local services）
├─ .env.example # ローカル用の雛形（秘密は入れない）
│
├─ docs/ # 仕様・図・未踏用資料
│ ├─ architecture.md
│ ├─ api-spec.md
│ └─ diagrams/
│ └─ system.drawio
│
├─ client/
│ ├─ unity/ # Unity プロジェクト（Editor は各 OS で）
│ │ ├─ Assets/
│ │ ├─ Packages/
│ │ └─ ProjectSettings/
│ │
│ └─ android-native/ # Android Studio プロジェクト（Unity 連携用）
│ ├─ settings.gradle.kts
│ ├─ build.gradle.kts
│ ├─ launcher/ # Unity 起動
│ ├─ unityLibrary/ # Unity export 生成物（触るの最小）
│ └─ commxr-native/ # ★Kotlin 本体（MediaPipe/音声/Bridge）
│ └─ src/main/...
│
├─ server/
│ ├─ api/ # FastAPI 本体（ECS に載せる）
│ │ ├─ app/
│ │ │ ├─ main.py
│ │ │ ├─ api/ # ルーター群
│ │ │ │ ├─ health.py
│ │ │ │ ├─ session.py # start/end/status
│ │ │ │ ├─ realtime.py # WS 接続管理（任意）
│ │ │ │ └─ suggest.py # LLM から候補生成
│ │ │ ├─ core/
│ │ │ │ ├─ config.py # env 読み込み（pydantic）
│ │ │ │ ├─ logging.py
│ │ │ │ └─ security.py # JWT/Cognito 検証など
│ │ │ ├─ services/
│ │ │ │ ├─ llm_service.py # Gemini/OpenAI など抽象化
│ │ │ │ ├─ stt_service.py # Transcribe 等（任意）
│ │ │ │ ├─ store_service.py # DynamoDB/S3
│ │ │ │ └─ moderation.py # 禁則/安全フィルタ
│ │ │ ├─ models/
│ │ │ │ ├─ schemas.py # request/response
│ │ │ │ └─ events.py # nonverbal features など
│ │ │ └─ utils/
│ │ ├─ tests/
│ │ ├─ requirements.txt # or pyproject.toml
│ │ ├─ Dockerfile # ★ ここ（サービス単位）
│ │ └─ .dockerignore
│ │
│ └─ worker/ # （任意）重処理/非同期（後で追加）
│ ├─ app/ # 例：ログ集計、バッチ評価
│ ├─ Dockerfile
│ └─ .dockerignore
│
├─ infra/
│ ├─ aws/ # IaC（どれか 1 つに寄せる）
│ │ ├─ terraform/ # 例：Terraform
│ │ │ ├─ modules/
│ │ │ └─ envs/
│ │ │ ├─ dev/
│ │ │ └─ prod/
│ │ └─ diagrams/ # AWS 構成図
│ └─ scripts/ # デプロイ補助（ECR push 等）
│
└─ .github/
└─ workflows/
├─ api-ci.yml # lint/test/build
└─ api-deploy.yml # ECR/ECS（後で）

# api サーバー

## パッケージマネージャー

uv

## docker

開発環境起動
```
uv run uvicorn main:app --reload
```

立ち上げ

```
docker compose up -d
```

終了

```
docker compose stop
```

## linter formatter

```
uv black .
uv ruff check .
```

自動修正
--fix を入れる
