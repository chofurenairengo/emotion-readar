# Emotion ReadAR（ERA）ソフトウェア仕様書

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

## 1. プロジェクト概要

### 1.1 概要

Emotion ReadAR（ERA）は、眼鏡型XRデバイスを用いた対面コミュニケーション支援システムである。会話相手の言語情報（会話内容）と非言語情報（表情・視線・声のトーン）をリアルタイムに解析し、ユーザーが次に発言すべき選択肢をアドバイスと共にHUD上に表示することで、対人能力を拡張する。

### 1.2 コンセプト

「コミュニケーションの補助輪（Social Training Wheels）」として、AIに依存させるのではなく、最終的にはデバイスなしで自立できることをゴールとした教育的・訓練的アプローチを取る。

### 1.3 設計ゴール

| ゴール                           | 説明                                                          |
| -------------------------------- | ------------------------------------------------------------- |
| 低遅延リアルタイムフィードバック | 即時フィードバック < 100ms、AIアドバイス 数秒〜十数秒         |
| プライバシー配慮                 | 顔画像・生音声の外部送信を最小化。送信は特徴量とテキストのみ  |
| 拡張可能なアーキテクチャ         | Clean Architecture による将来的なモデル変更・精度向上への対応 |
| MVP から本番まで耐える構成       | コスト最小化しつつスケーラブルな設計                          |

### 1.4 独創性

| 特徴                 | 説明                                                 |
| -------------------- | ---------------------------------------------------- |
| 補助輪設計           | AI依存ではなく自立をゴールとした教育的アプローチ     |
| シンプルな感情可視化 | 感情数値から「文字」と「絵文字」で直感的に表現       |
| 双方向コーチング     | 相手の解析だけでなく自己の振る舞いへのフィードバック |
| ドメイン特化LLM      | 恋愛・初対面など本音と建前が入り混じる文脈に特化     |

---

## 2. 解決する課題

1. **会話の行き詰まり**: 初対面や多人数での会話において、適切な話題や返答が見つからない
2. **非言語情報の見落とし**: 相手の微細な表情変化や自分の振る舞い（笑顔の不足・視線の不一致）に気づけない
3. **AI依存からの脱却**: AIチャットで満足してしまい現実世界の交流が疎かになる「AI閉じこもり」の防止

---

## 3. システムアーキテクチャ

### 3.1 全体構成（3層ハイブリッド）

エッジ・クラウドハイブリッド構成を採用。レイテンシが致命的な非言語情報抽出をエッジ端末で完結させ、計算リソースを要する文脈理解・推論生成をクラウドにオフロードする。

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          【1. 現実世界 (Reality)】                       │
│  [ 相手A ] [ 相手B ] ...           <========>      [ ユーザー (自分) ]   │
└───────┼────────┼───────────────────────────────────────┼─────────────────┘
        │光/音   │                                       │視覚 (HUD)
        ▼        ▼                                       │
┌─────────────────────────────────────────────────────────────────────────┐
│        【2. エッジデバイス (Android / Google AI Glass)】                  │
│  ┌─ [ A) Android Native (知覚・解析層) ] ──────────────────────────┐    │
│  │  ① 視覚解析 (MediaPipe)                                         │    │
│  │     ・顔検出 & ID管理                                            │    │
│  │     ・基本表情 (Blendshape) の瞬時判定                          │    │
│  │  ② 聴覚解析 (Audio Processing)                                  │    │
│  │     ・VAD(発話検知) / 音量 / ピッチ                              │    │
│  │     ・DOA(音源定位) → 話者特定                                   │    │
│  └─┬──────────────────────┬─────────────────────────────────────┘    │
│    │(1) 即時データ        │(2) ストリーミング送信                      │
│    │    (Local)           │    (Serverへ)                              │
│    ▼                      ▼                                            │
│  ┌─ [ B) Unity (表示・通信層) ] ──────────────────────────────┐        │
│  │  ■ [即時フィードバック] (遅延 < 100ms)                     │        │
│  │    ・基本感情絵文字を顔横に追従表示                        │        │
│  │    ・話者ハイライト                                        │        │
│  │  ■ [AIアドバイス表示] (数秒〜十数秒に1回)                  │        │
│  │    ・深層感情分析                                          │        │
│  │    ・会話戦略 / 回答候補 (2択)                             │        │
│  └────────────────────────────────────────────────────────┘        │
└───────────────────────────┼────────────────────────────────────────┘
                            │ WS (JSON + Audio Binary)
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    【3. クラウド (GCP)】                              │
│  ┌─ [ ERA Server (FastAPI / Clean Architecture) ] ──────────────┐  │
│  │  << 1. 蓄積・制御 (Orchestrator) >>                           │  │
│  │   ・STT (文字起こし)                                          │  │
│  │   ・文脈バッファ管理                                          │  │
│  │  << 2. 思考・生成 (LLM Agent) >>                              │  │
│  │   ・高速RAG検索 (Tavily)                                      │  │
│  │   ・マルチモーダル深層分析 (Gemini 2.5 Flash Fine-tuned)      │  │
│  │   ・アドバイス生成 (選択肢 x2)                                │  │
│  │  << 3. 出力 (Response Builder) >>                             │  │
│  │   ・JSON形式で返却                                            │  │
│  └──────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.2 データフロー

1. カメラ・マイク・センサー入力（現実世界の映像と音声）
2. Android で非言語特徴量をリアルタイム抽出（画像は即時破棄）
3. Unity で即時フィードバック表示（感情絵文字・話者ハイライト）
4. 音声＋特徴量をWebSocket経由でサーバーへ送信
5. STT（Cloud Speech-to-Text）でテキスト化
6. テキスト＋非言語特徴量＋会話コンテキストをLLM（Gemini）へ入力
7. 返答候補2パターン・状況分析・会話戦略を生成
8. 結果をWebSocketでクライアントへPush
9. Unity HUD上に描画

### 3.3 ソフトウェアアーキテクチャ（Clean Architecture）

サーバーサイドは3層のClean Architectureを採用する。

```
Layer 1: Foundation (土台・ルール)
├── app/core/         # インターフェース・設定・例外定義
├── app/models/       # ドメインエンティティ
└── app/dto/          # リクエスト/レスポンス型定義

Layer 2: Logic & Implementation (実処理)
├── app/services/     # ビジネスロジック・ユースケース
└── app/infra/        # 外部連携の実装（Firestore, Gemini等）

Layer 3: Entry Point (統合・入口)
├── app/api/          # HTTPエンドポイント・WebSocket
├── app/middleware/    # CORS・ロギング
└── app/main.py       # FastAPIインスタンス
```

**依存関係ルール**:
- Layer 1 は外部依存なし
- Layer 2 は Layer 1 のみに依存（Service と Infra は相互依存しない）
- Layer 3 は Layer 1, 2 に依存
- `__init__.py` は `app/` 直下にのみ配置

---

## 4. 技術スタック

### 4.1 サーバーサイド

| 項目           | 技術                      | バージョン | 選定理由                        |
| -------------- | ------------------------- | ---------- | ------------------------------- |
| 言語           | Python                    | 3.14       | AIライブラリとの親和性          |
| フレームワーク | FastAPI                   | >= 0.128.0 | 非同期処理・自動ドキュメント    |
| ASGIサーバー   | uvicorn                   | >= 0.40.0  | FastAPIとの標準組み合わせ       |
| バリデーション | Pydantic v2               | >= 2.12.5  | 型安全なデータバリデーション    |
| 設定管理       | pydantic-settings         | >= 2.12.0  | 環境変数の型安全な読み込み      |
| パッケージ管理 | uv                        | -          | 高速な依存関係解決              |
| LLM            | langchain-google-vertexai | >= 2.0.0   | Gemini API統合                  |
| STT            | google-cloud-speech       | >= 2.29.0  | Google Cloud Speech-to-Text     |
| 認証           | firebase-admin            | >= 6.6.0   | Firebase Authentication         |
| フォーマッター | Black                     | >= 25.12.0 | line-length: 88                 |
| リンター       | Ruff                      | >= 0.14.10 | E, F, W ルール                  |
| 型チェック     | mypy                      | >= 1.19.1  | strict モード + pydantic plugin |
| テスト         | pytest                    | >= 9.0.2   | pytest-asyncio >= 0.25.0        |

### 4.2 クライアントサイド（Android）

| 項目     | 技術            | 選定理由                        |
| -------- | --------------- | ------------------------------- |
| 言語     | Kotlin          | MediaPipe公式対応・低レイテンシ |
| UI       | Jetpack Compose | モダンUI構築                    |
| 画像認識 | MediaPipe       | 顔検出・Blendshape・視線解析    |
| ビルド   | Gradle          | Android標準                     |

### 4.3 クライアントサイド（Unity / XR）

| 項目     | 技術                        | 選定理由                          |
| -------- | --------------------------- | --------------------------------- |
| エンジン | Unity (2022.3 LTS+)         | VFX Graph・マルチプラットフォーム |
| XR SDK   | Android XR SDK (Jetpack XR) | Google AI Glass対応               |
| AR       | AR Foundation / OpenXR      | 標準規格準拠                      |
| 言語     | C#                          | Unity標準                         |

### 4.4 インフラストラクチャ

| 項目         | 技術                                        | 選定理由                  |
| ------------ | ------------------------------------------- | ------------------------- |
| クラウド     | Google Cloud (GCP)                          | Gemini APIとの親和性      |
| コンピュート | Cloud Run                                   | 従量課金・アイドル時$0    |
| データベース | Cloud Firestore (Native Mode)               | 無料枠が大きい            |
| 認証         | Firebase Authentication                     | 50K MAUまで無料           |
| 音声認識     | Cloud Speech-to-Text                        | 60分/月無料               |
| LLM          | Gemini 2.5 Flash (Fine-tuned) via Vertex AI | 高速・低コスト            |
| シークレット | Secret Manager                              | 6アクティブバージョン無料 |
| ログ         | Cloud Logging                               | 50GB/月無料               |

### 4.5 採用しないサービス

| サービス            | 理由                       | 代替                      |
| ------------------- | -------------------------- | ------------------------- |
| Cloud SQL           | 常時稼働で高コスト         | Firestore（サーバーレス） |
| GKE                 | 管理オーバーヘッド・コスト | Cloud Run                 |
| Cloud Load Balancer | 月額$18〜                  | Cloud Run標準URL          |
| Pub/Sub             | 小規模では不要             | WebSocket直接             |
| Cloud Functions     | 分散管理が煩雑             | Cloud Runに統合           |

---

## 5. クライアントサイド仕様

### 5.1 Android Native（知覚・解析層）

#### 5.1.1 画像認識（MediaPipe）

| 機能         | 詳細                                           |
| ------------ | ---------------------------------------------- |
| 顔検出       | Face Landmarker（最大468点のランドマーク推定） |
| 表情解析     | Blendshapeによる表情数値化                     |
| 視線推定     | 視線方向の数値化                               |
| 頭部姿勢     | 頭部の傾き・向きの推定                         |
| 人物追跡     | ID維持による複数人トラッキング                 |
| プライバシー | 生画像は即時破棄、数値特徴量のみ出力           |

#### 5.1.2 感情スコア算出（EmotionScoreCalculator）

8種類の感情を独立したスコアとして算出する（合計が1.0になる必要はない）。

| 感情キー    | 説明   | 値域      |
| ----------- | ------ | --------- |
| `happy`     | 喜び   | 0.0 - 1.0 |
| `sad`       | 悲しみ | 0.0 - 1.0 |
| `angry`     | 怒り   | 0.0 - 1.0 |
| `surprised` | 驚き   | 0.0 - 1.0 |
| `confused`  | 困惑   | 0.0 - 1.0 |
| `neutral`   | 中立   | 0.0 - 1.0 |
| `fearful`   | 恐怖   | 0.0 - 1.0 |
| `disgusted` | 嫌悪   | 0.0 - 1.0 |

#### 5.1.3 音声前処理

| 機能   | 詳細                                            |
| ------ | ----------------------------------------------- |
| VAD    | Voice Activity Detection（発話開始/終了の検知） |
| 音量   | エネルギーレベルの計測                          |
| ピッチ | 基本周波数の推定                                |
| 話速   | 発話速度の推定                                  |
| DOA    | Direction of Arrival（音源定位による話者特定）  |
| STT    | 端末側ではSTTを行わない（サーバー側で実施）     |

#### 5.1.4 音声録音・エンコード（AudioRecorder）

| 項目               | 仕様                      |
| ------------------ | ------------------------- |
| エンコード         | Base64                    |
| 対応フォーマット   | WAV (LINEAR16), PCM, OPUS |
| サンプリングレート | 16000Hz（デフォルト）     |
| 最大音声サイズ     | 5MB（Base64エンコード後） |
| 最大音声長         | 30秒                      |

### 5.2 Unity（表示・通信層）

#### 5.2.1 即時フィードバック（遅延 < 100ms）

- 基本感情絵文字（happy / surprised / angry 等）を会話相手の顔の横に追従表示
- 話者ハイライト（現在話している人の枠を強調）
- **非実装**: 警告機能（早口・声量注意など）

#### 5.2.2 AIアドバイス表示（数秒〜十数秒に1回）

- 深層感情分析（「退屈そう」「疑っている」等）
- 会話戦略タグ（「共感」「深掘り」等）
- 回答候補（2択）
- MFUI（Minimal Functional UI）で視界を遮らないシンプル表示

#### 5.2.3 データモデル（ANALYSIS_RESPONSE対応）

Unityは解析を行わず、サーバーからの `ANALYSIS_RESPONSE` を受信して意味付けと可視化に専念する。

受信データ:
- `emotion`: 感情解釈結果（primary_emotion, intensity, description, suggestion）
- `transcription`: STT結果（text, confidence, language, duration_ms）
- `suggestions`: 応答候補2パターン（text, intent）
- `situation_analysis`: 状況分析テキスト

#### 5.2.4 WebSocket通信

- 接続先: `ws://{server}/api/realtime?session_id={session_id}&token={firebase_id_token}`
- プロトコル: JSON（テキストフレーム）+ Base64エンコード音声
- ハートビート: PING/PONG

### 5.3 Android REST APIクライアント

| 操作           | メソッド | エンドポイント                   |
| -------------- | -------- | -------------------------------- |
| セッション作成 | POST     | `/api/sessions`                  |
| セッション取得 | GET      | `/api/sessions/{session_id}`     |
| セッション終了 | POST     | `/api/sessions/{session_id}/end` |
| 特徴量送信     | POST     | `/api/features`                  |

---

## 6. サーバーサイド仕様

> **詳細**: [docs/spec/server.md](docs/spec/server.md)

FastAPI + Clean Architectureによる3層構成。

### 主要コンポーネント

| コンポーネント       | 説明                                           |
| -------------------- | ---------------------------------------------- |
| `app/core/`          | インターフェース・設定・例外定義               |
| `app/services/`      | ビジネスロジック（9サービス）                  |
| `app/api/routers/`   | REST/WebSocketエンドポイント                   |
| `app/infra/`         | 外部連携（Firestore, Gemini）                  |

### 必須環境変数

| 環境変数         | 説明                |
| ---------------- | ------------------- |
| `GCP_PROJECT_ID` | GCPプロジェクトID   |
| `FT_MODEL_ID`    | Fine-tuned Gemini名 |

---

## 7. API仕様

> **詳細**: [docs/spec/api.md](docs/spec/api.md)

REST API と WebSocket API を提供。

### エンドポイント一覧

| メソッド  | パス                             | 説明             |
| --------- | -------------------------------- | ---------------- |
| GET       | `/api/health`                    | ヘルスチェック   |
| POST      | `/api/sessions`                  | セッション作成   |
| GET       | `/api/sessions/{id}`             | セッション取得   |
| POST      | `/api/sessions/{id}/end`         | セッション終了   |
| POST      | `/api/features`                  | 特徴量送信       |
| WebSocket | `/api/realtime`                  | リアルタイム通信 |

### WebSocketメッセージタイプ

| 方向       | タイプ              | 説明           |
| ---------- | ------------------- | -------------- |
| C → S      | `ANALYSIS_REQUEST`  | 解析リクエスト |
| S → C      | `ANALYSIS_RESPONSE` | 解析結果       |

### レート制限

| エンドポイント  | 制限       |
| --------------- | ---------- |
| `/api/sessions` | 30 req/min |
| その他          | 100 req/min|

---

## 8. データモデル・DTO定義

> **詳細**: [docs/spec/models.md](docs/spec/models.md)

Pydantic v2によるDTO定義とドメインモデル。

### 主要DTO

| DTO                     | 説明                   |
| ----------------------- | ---------------------- |
| `AnalysisRequest`       | WebSocket解析リクエスト|
| `AnalysisResponse`      | WebSocket解析レスポンス|
| `EmotionInterpretation` | 感情解釈結果           |
| `ResponseSuggestion`    | 応答候補               |

### ドメインモデル

| モデル       | 説明                     |
| ------------ | ------------------------ |
| `Session`    | セッション（active/ended）|
| `FeatureLog` | 特徴量ログ               |

---

## 9. サービス層仕様

> **詳細**: [docs/spec/services.md](docs/spec/services.md)

9つのサービスによるビジネスロジック。

### サービス一覧

| サービス                  | 説明                      |
| ------------------------- | ------------------------- |
| `STTService`              | 音声認識（Cloud Speech）  |
| `ConversationService`     | 会話履歴管理              |
| `EmotionInterpreterService` | 感情解釈・行動提案      |
| `LLMService`              | 応答候補生成（Gemini）    |
| `ResponseGeneratorService`| 統合オーケストレーター    |
| `SessionService`          | セッション管理            |
| `InMemoryRateLimiter`     | レート制限                |
| `ConnectionManager`       | WebSocket接続管理         |
| `LoveCoachAgent`          | AIエージェント            |

### 処理パイプライン概要

```
音声 → STT → 会話履歴追加 → 感情解釈 → LLM推論 → 応答生成
```

---

## 10. インフラストラクチャ

### 10.1 GCPサービス構成

| 用途         | サービス                      | 月額コスト試算 |
| ------------ | ----------------------------- | -------------- |
| API Server   | Cloud Run                     | $0〜5          |
| データベース | Firestore (Native Mode)       | $0（無料枠内） |
| 認証         | Firebase Authentication       | $0（無料枠内） |
| 音声認識     | Cloud Speech-to-Text          | $14（10時間）  |
| LLM          | Gemini 2.5 Flash (Fine-tuned) | $0〜           |
| シークレット | Secret Manager                | $0（無料枠内） |
| **合計**     |                               | **$15〜20/月** |

### 10.2 Cloud Run設定

```yaml
min-instances: 0          # アイドル時課金なし
max-instances: 3          # スパイク対策
cpu-throttling: true      # リクエスト処理時のみCPU課金
memory: 512Mi-1Gi         # 最小限
timeout: 300s             # WebSocket対応
```

### 10.3 Docker構成

**Dockerfile**: マルチステージビルド（uv + uvicorn）

**docker-compose.yml**: ローカル開発環境

| サービス           | ポート | 説明                 |
| ------------------ | ------ | -------------------- |
| api                | 8000   | FastAPIサーバー      |
| firestore-emulator | 8080   | Firestore Emulator   |
| firestore-ui       | 4000   | Firebase Emulator UI |

### 10.4 リポジトリ層

#### 現在の実装: InMemorySessionRepository

開発用のインメモリ実装。サーバー再起動で全データ消失。

#### 計画中: FirestoreSessionRepository / FirestoreConversationRepository

永続化層の実装（Issue #42 Epic: Firestore永続化層の実装）

| Issue | 内容                                       | ステータス |
| ----- | ------------------------------------------ | ---------- |
| #44   | Firestore依存関係・クライアント設定        | OPEN       |
| #45   | SessionRepositoryインターフェース拡張      | OPEN       |
| #46   | ConversationRepositoryインターフェース定義 | OPEN       |
| #47   | FirestoreSessionRepository実装             | OPEN       |
| #48   | FirestoreConversationRepository実装        | OPEN       |
| #49   | 依存性注入の環境切り替え対応               | OPEN       |
| #50   | Firestoreリポジトリ単体テスト              | OPEN       |

---

## 11. セキュリティ仕様

### 11.1 認証

- **方式**: Firebase Authentication（Firebase ID Token / JWT）
- **検証**: firebase-admin SDKによるIDトークン検証
- **対象**: 全REST APIエンドポイント（`/api/health` を除く）、WebSocket接続
- **MAU上限**: 50,000（Firebase無料枠）

### 11.2 認可

- セッション所有者検証（セッション操作は作成者のみ）
- WebSocket接続時のトークン検証 + セッション権限確認

### 11.3 CORS設定

- 開発環境: `localhost` 各ポートを自動追加
- 本番環境: `ALLOWED_ORIGINS` 環境変数で明示的に指定
- 許可メソッド: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`
- 認証ヘッダー: `Authorization`, `Content-Type`

### 11.4 レート制限

- Sliding Windowアルゴリズム
- エンドポイント別の制限値
- レスポンスヘッダーで残数通知

### 11.5 通信暗号化

- 全通信はTLS暗号化
- 秘密情報はSecret Managerで管理

### 11.6 WebSocket Close Codes

| コード | 説明                 |
| ------ | -------------------- |
| 4001   | トークン検証失敗     |
| 4003   | セッション権限エラー |
| 4004   | セッション未発見     |

---

## 12. プライバシー方針

| ポリシー     | 詳細                                              |
| ------------ | ------------------------------------------------- |
| 顔画像・映像 | クラウドに送信しない。端末内で処理後即時破棄      |
| 生音声       | 保存しない（同意時のみ例外）                      |
| 送信データ   | 特徴量（数値）とテキストのみ                      |
| 通信         | TLS暗号化                                         |
| 秘密情報     | Secret Manager管理                                |
| データ保持   | セッション終了後の会話履歴は保持しない（MVP段階） |

---

## 13. テスト仕様

### 13.1 テスト方針

- **最低カバレッジ**: 80%
- **TDD**: テストファースト開発を推奨
- **フレームワーク**: pytest + pytest-asyncio

### 13.2 実装済みテスト（20ファイル）

| テストファイル                 | 対象                         | カテゴリ       |
| ------------------------------ | ---------------------------- | -------------- |
| `test_health.py`               | ヘルスチェックエンドポイント | 単体           |
| `test_dto.py`                  | DTOバリデーション            | 単体           |
| `test_auth.py`                 | 認証・トークン検証           | 単体           |
| `test_firebase.py`             | Firebase初期化               | 単体           |
| `test_cors.py`                 | CORSミドルウェア             | 結合           |
| `test_rate_limiter.py`         | レート制限ロジック           | 単体           |
| `test_rate_limit.py`           | レート制限API統合            | 結合           |
| `test_conversation_service.py` | 会話履歴管理                 | 単体           |
| `test_emotion_interpreter.py`  | 感情解釈ロジック             | 単体           |
| `test_stt_service.py`          | 音声認識                     | 単体（モック） |
| `test_llm_service.py`          | LLMサービス（リトライ含む）  | 単体（モック） |
| `test_response_generator.py`   | 応答生成パイプライン         | 結合（モック） |
| `test_realtime.py`             | WebSocket統合                | 結合           |

### 13.3 テスト実行方法

```bash
cd server
uv run pytest
```

---

## 14. CI/CD

### 14.1 GitHub Actions ワークフロー

**トリガー**: `main` ブランチへのpush / PR

**ジョブ**: `lint-and-format`

| ステップ | コマンド                  | 説明                 |
| -------- | ------------------------- | -------------------- |
| 1        | `uv sync`                 | 依存関係インストール |
| 2        | `uv run ruff check --fix` | リントチェック       |
| 3        | `uv run black . --check`  | フォーマットチェック |

### 14.2 コード品質コマンド

```bash
# リンター
uv run ruff check .
uv run ruff check . --fix  # 自動修正

# フォーマッター
uv run black .
uv run black . --check  # チェックのみ

# 型チェック
uv run mypy .

# テスト
uv run pytest
```

---

## 15. 非機能要件

### 15.1 パフォーマンス

| 項目                      | 目標値                 |
| ------------------------- | ---------------------- |
| 即時フィードバック遅延    | < 100ms（エッジ側）    |
| ANALYSIS_RESPONSE応答時間 | 数秒〜十数秒           |
| WebSocket接続維持         | Cloud Run timeout 300s |

### 15.2 スケーラビリティ

| 項目                      | 設定                                                |
| ------------------------- | --------------------------------------------------- |
| Cloud Run最小インスタンス | 0                                                   |
| Cloud Run最大インスタンス | 3                                                   |
| セッション数上限          | インメモリのため制限あり（Firestore移行で解消予定） |

### 15.3 可用性

- Cloud Run自動スケーリング
- ヘルスチェックエンドポイント
- エラー時のグレースフルデグラデーション（STT失敗時も処理継続）

### 15.4 命名規約

| 対象               | 規約                  |
| ------------------ | --------------------- |
| Python 関数・変数  | `snake_case`          |
| Python クラス      | `PascalCase`          |
| Kotlin 関数・変数  | `camelCase`           |
| Kotlin / C# クラス | `PascalCase`          |
| Black line-length  | 88                    |
| Ruff ルール        | E, F, W（E501は除外） |
| 型ヒント           | 必須（mypy strict）   |

---

## 16. 開発環境

### 16.1 バックエンドセットアップ

```bash
cd server

# 依存関係インストール
uv sync

# 開発ツール含む
uv sync --group dev

# 開発サーバー起動（ホットリロード有効）
uv run uvicorn main:app --reload
```

### 16.2 Docker開発環境

```bash
# 起動
docker compose up -d

# 停止
docker compose stop

# ログ確認
docker compose logs -f api
```

### 16.3 環境変数（ローカル開発）

```bash
# .env ファイル
GCP_PROJECT_ID=your-project-id
FIRESTORE_EMULATOR_HOST=firestore-emulator:8080
FIRESTORE_PROJECT_ID=dev-project
ENV_STATE=dev

# GCP認証
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
# または: gcloud auth application-default login
```

### 16.4 Android開発

```bash
cd client/android

# デバッグビルド
./gradlew assembleDebug

# ユニットテスト
./gradlew test

# 結合テスト（エミュレータ/実機必須）
./gradlew connectedAndroidTest
```

### 16.5 Unity開発

Unity Editor で `client/Mitou` を開いて開発。

---

## 17. 今後の拡張計画

### 17.1 Firestore永続化（Epic #42）

セッション・会話履歴のインメモリストレージからFirestoreへの移行。

### 17.2 Secret Manager連携（Issue #58）

環境変数からSecret Managerへのシークレット管理移行。

### 17.3 RAG検索（Issue #23）

Tavilyを用いたWeb検索による会話中の不明単語・最新ニュースの参照。

### 17.4 Android機能実装

| Issue  | 内容                             |
| ------ | -------------------------------- |
| #84    | CameraX統合とプレビュー          |
| #85    | MediaPipe Face Landmarker統合    |
| #86    | 音声録音とエンコード             |
| #76    | AudioRecorder - Base64エンコード |
| #87    | メイン画面統合                   |
| #80    | MainViewModel + UseCase          |
| #82-83 | 共有データモデル・UIテーマ       |

### 17.5 Unity機能実装

| Issue | 内容                                |
| ----- | ----------------------------------- |
| #77   | ANALYSIS_RESPONSE対応データモデル   |
| #78   | WebSocketClient                     |
| #79   | HUD表示（感情・返答候補・状況分析） |

### 17.6 将来的な拡張

| 拡張                       | 説明                                       |
| -------------------------- | ------------------------------------------ |
| 話者分離・方向推定の高度化 | DOA精度向上                                |
| マルチユーザー対応         | 複数セッション同時処理                     |
| 成長指標の長期可視化       | コミュニケーション能力の成長追跡           |
| オフラインモード           | 限定機能でのオフライン動作                 |
| ドメイン特化チューニング   | LoRAによる恋愛・親睦シーン特化             |
| 自己解析                   | 内カメラによる自分の振る舞いフィードバック |

---

## 18. 非対象（Non-Goals）

- 端末での高精度STT（サーバー側で実施）
- 顔画像の長期保存
- 感情の断定（「怒っている」と断言しない）
- フル自動会話（人が話す前提）
- 警告機能（早口・声量注意など）

---

## 付録

### A. 関連ドキュメント

| ドキュメント     | パス                                 | 説明                               |
| ---------------- | ------------------------------------ | ---------------------------------- |
| README.md        | `/README.md`                         | 設計仕様書                         |
| CLAUDE.md        | `/CLAUDE.md`                         | AIアシスタント向けガイド           |
| AGENTS.md        | `/AGENTS.md`                         | AIエージェント向け詳細ガイドライン |
| API仕様          | `/docs/api.md`                       | API詳細リファレンス                |
| アーキテクチャ図 | `/docs/architecture.dio`             | Draw.ioアーキテクチャ図            |
| サーバーMVP Epic | `/docs/issues/00_epic_server_mvp.md` | サーバーサイドMVP実装計画          |

### B. コスト試算（MVP/小規模運用）

| サービス         | 想定使用量     | 月額           |
| ---------------- | -------------- | -------------- |
| Cloud Run        | 10万リクエスト | $0〜5          |
| Firestore        | 無料枠内       | $0             |
| Firebase Auth    | 無料枠内       | $0             |
| Speech-to-Text   | 10時間         | $14            |
| Gemini 2.5 Flash | 100万トークン  | $0〜           |
| Secret Manager   | 無料枠内       | $0             |
| **合計**         |                | **$15〜20/月** |

---

## 19. 更新履歴

<!-- AUTO:UPDATE_HISTORY:START -->
<details>
<summary>仕様書更新</summary>

| 日付       | 内容                      | 更新者 |
| ---------- | ------------------------- | ------ |
| 2026-02-03 | #145 #141 feat: ヘルスチェックにモデル接続状態を追加 | @miyabi206 |
| 2026-02-02 | #132 feat: 仕様書自動作成機能を追加 | @miyabi206 |

</details>

<details>
<summary>プログラム更新</summary>

| 日付       | 内容                         | 更新者 |
| ---------- | ---------------------------- | ------ |
| 2026-02-03 | #145 #141 feat: ヘルスチェックにモデル接続状態を追加 | @miyabi206 |
| 2026-02-03 | #146 Feature/manifestjson bug | @93tajam |
| 2026-02-02 | #132 feat: 仕様書自動作成機能を追加 | @miyabi206 |

</details>
<!-- AUTO:UPDATE_HISTORY:END -->

## 20. 開発者

<!-- AUTO:DEVELOPERS:START -->
| 開発者 |
| ------ |
| Daccho |
| miyabi206 |
| 93tajam |
| claude |
<!-- AUTO:DEVELOPERS:END -->

## 21. 過去のIssue

<!-- AUTO:CLOSED_ISSUES:START -->
- #142 docs: .env.example にFTモデル設定例を追記
- #140 feat: LLM_TEMPERATURE を設定から変更可能にする
- #130 仕様書の自動更新機能
- #123 [Unity] Quest 3向けXR設定とビルド環境構築
- #105 claudeのフローにprのmdを作るところまでカスタム指示を追加する
- #95 copilot と claude code コマンドを修正
- #87 [Android] app - メイン画面統合（カメラ+音声+感情表示）
- #86 [Android] feature/audio - 音声録音とエンコード
- #85 [Android] feature/camera - MediaPipe Face Landmarker統合
- #84 [Android] feature/camera - CameraX統合とプレビュー
- #83 [Android] core/ui - 共通UIテーマ・コンポーネント
- #82 [Android] core/model - 共有データモデル定義
- #80 [Android] MainViewModel + UseCase - セッション・接続状態管理
- #79 [Unity] HUD表示 - 感情・返答候補・状況分析
- #78 [Unity] WebSocketClient - サーバー接続とANALYSIS_RESPONSE受信
- #77 [Unity] データモデル - ANALYSIS_RESPONSE対応
- #76 [Android] AudioRecorder - 音声録音とBase64エンコード
- #75 [Android] WebSocketClient - サーバーリアルタイム通信
- #74 [Android] REST APIクライアント - セッション管理
- #73 [Android] EmotionScoreCalculator - 8種類の感情スコア算出
- #72 [Chore] PR #12 クリーンアップ - 不要ファイル削除・.gitignore整備
- #59 レート制限の実装
- #58 Secret Manager連携
- #57 CORS設定の本番対応
- #56 セッション所有者検証
- #55 JWT検証ミドルウェア実装
- #54 Firebase Admin SDK導入・設定
- #51 PR #17 をissue仕様に合わせて改善
- #47 FirestoreSessionRepository実装
- #46 ConversationRepositoryインターフェース定義
- #45 SessionRepositoryインターフェース拡張
- #44 Firestore依存関係・クライアント設定
- #37 WebSocketハンドラの拡張
- #36 応答生成サービス（統合）の実装
- #35 LLMサービスの実装
- #34 感情解釈サービスの実装
- #33 会話履歴管理サービスの実装
- #32 STTサービスの実装
- #31 DTO定義（型定義）の実装
- #30 [Epic] サーバーサイドMVP実装 - 感情解析・音声認識・LLM応答生成
- #25 lint&format自動化
<!-- AUTO:CLOSED_ISSUES:END -->

## 22. 現在のIssue

<!-- AUTO:OPEN_ISSUES:START -->
| #    | タイトル               | 担当 | 概要                                                 |
| ---- | ---------------------- | ---- | ---------------------------------------------------- |
| #148 | [Unity] Quest/Android用ビルド自動化スク | - | ## 概要 |
| #147 | [Unity] 通常Android端末でXR自動初期化による | - | ## 概要 |
| #141 | feat: ヘルスチェックにモデル接続状態を追加 | - | ## 概要 |
| #139 | feat: Vertex AIファインチューニング済みGem | @miyabi206 | ## 概要 |
| #129 | q | @Daccho | ### 概要 |
| #127 | [Unity] メイン画面統合 - Android/XR共通 | @Daccho | ## 概要 |
| #106 | env.exampleの作成 | - | ### 概要 |
| #98 | claude code skills, rules, age | @miyabi206 | 必要なskills, agentsなどに絞る |
| #70 | refactor: Speaker, Utterance,  | @Daccho | ## 概要 |
| #69 | projectをissueだけ表示してみやすくしたい | @Daccho | ### 概要 |
| #53 | [Epic] 認証・認可基盤の実装 | @miyabi206 | ## 概要 |
| #50 | Firestoreリポジトリ単体テスト | @Daccho | ## 概要 |
| #49 | 依存性注入の環境切り替え対応 | @Daccho | ## 概要 |
| #48 | FirestoreConversationRepositor | @Daccho | ## 概要 |
| #42 | [Epic] Firestore永続化層の実装 | @Daccho | ## 概要 |
| #29 | 会話履歴管理サービスの実装 | @Daccho | ### 概要 |
| #28 | 音声データをテキストに変換する音声認識（Speech-to- | @Daccho | ### 概要 |
| #27 | DTO（Data Transfer Object）を定義 | @Daccho | ### 概要 |
| #23 | rag | @miyabi206 | ### 概要 |
<!-- AUTO:OPEN_ISSUES:END -->

## 23. 開発予定

<!-- AUTO:ROADMAP:START -->
### Epicベースの開発予定

現在オープンなEpicはありません。

### 将来的な拡張

| 拡張 | 説明 |
|------|------|
| 話者分離・方向推定の高度化 | DOA精度向上 |
| マルチユーザー対応 | 複数セッション同時処理 |
| 成長指標の長期可視化 | コミュニケーション能力の成長追跡 |
| オフラインモード | 限定機能でのオフライン動作 |
| ドメイン特化チューニング | LoRAによる恋愛・親睦シーン特化 |
| 自己解析 | 内カメラによる自分の振る舞いフィードバック |
<!-- AUTO:ROADMAP:END -->
