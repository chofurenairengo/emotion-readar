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

### 6.1 ディレクトリ構成

```
server/
├── app/
│   ├── __init__.py
│   ├── main.py                    # FastAPIファクトリ
│   ├── api/
│   │   ├── auth.py                # JWT検証 + レート制限
│   │   ├── dependencies.py        # 依存性注入
│   │   └── routers/
│   │       ├── health.py          # ヘルスチェック
│   │       ├── sessions.py        # セッション管理
│   │       ├── features.py        # 特徴量受信
│   │       └── realtime.py        # WebSocket
│   ├── core/
│   │   ├── config.py              # Settings (Pydantic Settings)
│   │   ├── exceptions.py          # カスタム例外
│   │   ├── interfaces/            # Protocol定義（5インターフェース）
│   │   └── prompts/               # LLMプロンプト管理
│   ├── dto/                       # DTO定義（11ファイル）
│   │   ├── request.py             # FeatureRequest
│   │   ├── response.py            # FeatureResponse, SessionResponse
│   │   ├── audio.py               # AudioFormat, TranscriptionResult
│   │   ├── conversation.py        # Speaker, Utterance, EmotionContext
│   │   ├── emotion.py             # EmotionInterpretation, EmotionChange
│   │   ├── llm.py                 # ResponseSuggestion, LLMResponseResult
│   │   └── processing.py          # AnalysisRequest, AnalysisResponse
│   ├── models/
│   │   ├── session.py             # Session, FeatureLog
│   │   └── ...
│   ├── services/
│   │   ├── session_service.py     # セッション管理
│   │   ├── feature_service.py     # 特徴量管理
│   │   ├── rate_limiter.py        # レート制限（Sliding Window）
│   │   ├── conversation_service.py # 会話履歴管理
│   │   ├── emotion_interpreter.py # 感情解釈
│   │   ├── stt_service.py         # 音声認識
│   │   ├── llm_service.py         # LLM推論
│   │   ├── response_generator.py  # 応答生成（統合オーケストレーター）
│   │   ├── health_service.py      # ヘルスチェック
│   │   ├── connection_manager.py  # WebSocket接続管理
│   │   └── agents/
│   │       └── love_coach.py      # AIエージェント
│   ├── infra/
│   │   ├── firebase.py            # Firebase Admin SDK初期化
│   │   ├── external/
│   │   │   └── gemini_client.py   # LLMクライアントファクトリ
│   │   └── repositories/
│   │       └── in_memory_session_repo.py
│   └── middleware/
│       ├── cors.py                # CORS設定
│       └── logging.py             # リクエストロギング
├── tests/                         # テストコード（20ファイル）
├── main.py                        # uvicornエントリーポイント
├── pyproject.toml                 # プロジェクト設定
├── uv.lock                        # 依存関係ロック
├── Dockerfile                     # マルチステージビルド
└── .python-version                # Python 3.14
```

### 6.2 設定管理（config.py）

| 環境変数                         | 型  | デフォルト        | 説明                               |
| -------------------------------- | --- | ----------------- | ---------------------------------- |
| `GCP_PROJECT_ID`                 | str | （必須）          | GCPプロジェクトID                  |
| `GCP_LOCATION`                   | str | `asia-northeast1` | Vertex AIリージョン                |
| `FT_MODEL_ID`                    | str | -                 | Fine-tuned Geminiモデル名          |
| `ENV_STATE`                      | str | `dev`             | 環境（dev / prod）                 |
| `ALLOWED_ORIGINS`                | str | -                 | CORS許可オリジン（カンマ区切り）   |
| `RATE_LIMIT_DEFAULT`             | int | `100`             | デフォルトレート制限（req/min）    |
| `RATE_LIMIT_WINDOW_SECONDS`      | int | `60`              | レート制限ウィンドウ（秒）         |
| `FIRESTORE_EMULATOR_HOST`        | str | -                 | ローカル開発時のエミュレータホスト |
| `GOOGLE_APPLICATION_CREDENTIALS` | str | -                 | ADCキーパス（通常は自動検出、設定不要） |

### 6.3 例外階層

```
ERAException (ベース例外)
├── STTError              # 音声認識エラー
├── LLMError              # LLM推論エラー
│   ├── LLMRateLimitError # レートリミットエラー
│   └── LLMResponseParseError # レスポンスパースエラー
├── SessionPermissionError # セッション権限エラー
└── ...
```

---

## 7. API仕様

### 7.1 REST API

#### 7.1.1 ヘルスチェック

```
GET /api/health
```

**レスポンス** (200 OK):
```json
{
  "status": "ok"
}
```

#### 7.1.2 セッション作成

```
POST /api/sessions
Authorization: Bearer {Firebase ID Token}
```

**レスポンス** (201 Created):
```json
{
  "id": "abc123",
  "status": "active",
  "started_at": "2024-01-01T12:00:00Z",
  "ended_at": null
}
```

**レート制限**: 30 req/min

#### 7.1.3 セッション取得

```
GET /api/sessions/{session_id}
Authorization: Bearer {Firebase ID Token}
```

**レスポンス** (200 OK):
```json
{
  "id": "abc123",
  "status": "active",
  "started_at": "2024-01-01T12:00:00Z",
  "ended_at": null
}
```

**エラー** (404 Not Found):
```json
{
  "detail": "Not found"
}
```

#### 7.1.4 セッション終了

```
POST /api/sessions/{session_id}/end
Authorization: Bearer {Firebase ID Token}
```

**レスポンス** (200 OK):
```json
{
  "id": "abc123",
  "status": "ended",
  "started_at": "2024-01-01T12:00:00Z",
  "ended_at": "2024-01-01T13:00:00Z"
}
```

#### 7.1.5 特徴量受信

```
POST /api/features
Authorization: Bearer {Firebase ID Token}
```

**リクエスト**:
```json
{
  "session_id": "abc123",
  "timestamp": "2024-01-01T12:00:00Z",
  "facial": {
    "smile": 0.85,
    "anger": 0.1
  },
  "gaze": {
    "direction_x": 0.5,
    "direction_y": -0.2
  },
  "voice": {
    "energy": 0.7,
    "pitch": 0.6
  },
  "extras": {}
}
```

| フィールド   | 型             | 必須 | 説明                                  |
| ------------ | -------------- | ---- | ------------------------------------- |
| `session_id` | string \| null | No   | セッションID                          |
| `timestamp`  | string \| null | No   | ISO 8601 タイムスタンプ               |
| `facial`     | object \| null | No   | 顔特徴量（キー: 特徴名, 値: 0.0-1.0） |
| `gaze`       | object \| null | No   | 視線特徴量                            |
| `voice`      | object \| null | No   | 音声特徴量                            |
| `extras`     | object \| null | No   | その他の任意データ                    |

**レスポンス** (202 Accepted):
```json
{
  "id": "log123",
  "session_id": "abc123",
  "received_at": "2024-01-01T12:00:00Z",
  "status": "accepted"
}
```

**レート制限**: 100 req/min

### 7.2 WebSocket API

#### 7.2.1 接続

```
WebSocket /api/realtime?session_id={session_id}&token={firebase_id_token}
```

| パラメータ   | 型     | 必須 | 説明                |
| ------------ | ------ | ---- | ------------------- |
| `session_id` | string | Yes  | セッションID        |
| `token`      | string | Yes  | Firebase IDトークン |

**WebSocket Close Codes**:

| コード | 説明                 |
| ------ | -------------------- |
| 4001   | トークン検証失敗     |
| 4003   | セッション権限エラー |
| 4004   | セッション未発見     |

#### 7.2.2 メッセージタイプ一覧

**クライアント → サーバー**:

| タイプ             | 説明               |
| ------------------ | ------------------ |
| `PING`             | 接続確認           |
| `RESET`            | セッションリセット |
| `ERROR_REPORT`     | エラー報告         |
| `ANALYSIS_REQUEST` | 解析リクエスト     |

**サーバー → クライアント**:

| タイプ              | 説明         |
| ------------------- | ------------ |
| `PONG`              | 接続確認応答 |
| `RESET_ACK`         | リセット確認 |
| `ERROR_ACK`         | エラー確認   |
| `ERROR`             | エラー通知   |
| `ANALYSIS_RESPONSE` | 解析結果     |

#### 7.2.3 PING / PONG

```json
// クライアント → サーバー
{ "type": "PING" }

// サーバー → クライアント
{ "type": "PONG", "timestamp": "2024-01-01T12:00:00Z" }
```

#### 7.2.4 RESET / RESET_ACK

```json
// クライアント → サーバー
{ "type": "RESET" }

// サーバー → クライアント
{ "type": "RESET_ACK", "timestamp": "2024-01-01T12:00:00Z" }
```

#### 7.2.5 ERROR_REPORT / ERROR_ACK

```json
// クライアント → サーバー
{ "type": "ERROR_REPORT", "message": "エラー詳細" }

// サーバー → クライアント
{ "type": "ERROR_ACK", "timestamp": "2024-01-01T12:00:00Z" }
```

#### 7.2.6 ANALYSIS_REQUEST / ANALYSIS_RESPONSE

**リクエスト** (クライアント → サーバー):
```json
{
  "type": "ANALYSIS_REQUEST",
  "session_id": "abc123",
  "timestamp": "2024-01-01T12:00:00Z",
  "emotion_scores": {
    "happy": 0.8,
    "sad": 0.1,
    "angry": 0.05,
    "confused": 0.05
  },
  "audio_data": "Base64エンコードされた音声データ",
  "audio_format": "opus"
}
```

| フィールド       | 型                | 必須 | 説明                                     |
| ---------------- | ----------------- | ---- | ---------------------------------------- |
| `type`           | string            | Yes  | `"ANALYSIS_REQUEST"` 固定                |
| `session_id`     | string            | Yes  | セッションID                             |
| `timestamp`      | string (ISO 8601) | Yes  | クライアントタイムスタンプ               |
| `emotion_scores` | object            | Yes  | MediaPipeで算出された感情スコア          |
| `audio_data`     | string \| null    | No   | Base64エンコードされた音声データ         |
| `audio_format`   | string \| null    | No   | 音声フォーマット（"opus", "wav", "pcm"） |

**レスポンス** (サーバー → クライアント):
```json
{
  "type": "ANALYSIS_RESPONSE",
  "timestamp": "2024-01-01T12:00:01Z",
  "emotion": {
    "primary_emotion": "happy",
    "intensity": "high",
    "description": "相手は楽しそうです",
    "suggestion": "話題を広げましょう"
  },
  "transcription": {
    "text": "認識されたテキスト",
    "confidence": 0.95,
    "language": "ja",
    "duration_ms": 3000
  },
  "suggestions": [
    {
      "text": "それは面白いですね",
      "intent": "共感を示す"
    },
    {
      "text": "もっと詳しく教えて",
      "intent": "質問する"
    }
  ],
  "situation_analysis": "相手は説明を求めています",
  "processing_time_ms": 250
}
```

| フィールド                  | 型                          | 必須 | 説明                              |
| --------------------------- | --------------------------- | ---- | --------------------------------- |
| `type`                      | string                      | Yes  | `"ANALYSIS_RESPONSE"`             |
| `timestamp`                 | string                      | Yes  | サーバータイムスタンプ (ISO 8601) |
| `emotion`                   | EmotionInterpretation       | Yes  | 感情解釈結果                      |
| `emotion.primary_emotion`   | string                      | Yes  | 主要感情                          |
| `emotion.intensity`         | string                      | Yes  | 強度 ("low" / "medium" / "high")  |
| `emotion.description`       | string                      | Yes  | 自然言語での説明                  |
| `emotion.suggestion`        | string \| null              | No   | 行動提案                          |
| `transcription`             | TranscriptionResult \| null | No   | STT結果（音声がある場合）         |
| `transcription.text`        | string                      | Yes  | 認識されたテキスト                |
| `transcription.confidence`  | number                      | Yes  | 信頼度 (0.0-1.0)                  |
| `transcription.language`    | string                      | Yes  | 検出言語 ("ja", "en" 等)          |
| `transcription.duration_ms` | number                      | Yes  | 音声の長さ（ミリ秒）              |
| `suggestions`               | ResponseSuggestion[]        | Yes  | 応答候補（2パターン）             |
| `suggestions[].text`        | string                      | Yes  | 応答文                            |
| `suggestions[].intent`      | string                      | Yes  | 意図                              |
| `situation_analysis`        | string                      | Yes  | 状況分析                          |
| `processing_time_ms`        | number                      | Yes  | 処理時間（ミリ秒）                |

#### 7.2.7 ERROR

```json
{
  "type": "ERROR",
  "message": "Invalid session",
  "detail": null,
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### 7.2.8 WebSocketエラーメッセージ一覧

| エラーメッセージ               | 説明                     |
| ------------------------------ | ------------------------ |
| `Invalid JSON`                 | JSONパースエラー         |
| `Unsupported message type`     | 未対応のメッセージタイプ |
| `Missing required fields: ...` | 必須フィールド不足       |
| `Service not available`        | サービス未実装           |
| `Analysis failed: ...`         | 解析処理エラー           |

#### 7.2.9 接続シーケンス

```
[Client]                                    [Server]
    │                                           │
    │ ──── WebSocket Connect ────────────────▶  │
    │      ?session_id=xxx&token=yyy            │
    │                                           │
    │ ◀─── Connection Established ────────────  │
    │                                           │
    │ ──── PING ─────────────────────────────▶  │
    │ ◀─── PONG ─────────────────────────────   │
    │                                           │
    │ ──── ANALYSIS_REQUEST ─────────────────▶  │
    │      (emotion_scores + audio_data)        │
    │                                           │
    │      [STT処理]                            │
    │      [感情解釈]                            │
    │      [LLM推論]                            │
    │                                           │
    │ ◀─── ANALYSIS_RESPONSE ────────────────   │
    │      (emotion + suggestions)              │
    │                                           │
```

### 7.3 HTTPエラーレスポンス

| ステータス            | 説明                              |
| --------------------- | --------------------------------- |
| 404 Not Found         | セッション/リソースが見つからない |
| 429 Too Many Requests | レート制限超過                    |

### 7.4 レート制限

Sliding Windowアルゴリズムによるインメモリレート制限。

| エンドポイント  | 制限                      |
| --------------- | ------------------------- |
| `/api/sessions` | 30 req/min                |
| `/api/features` | 100 req/min               |
| その他          | 100 req/min（デフォルト） |

**レスポンスヘッダー**:

| ヘッダー                | 説明             |
| ----------------------- | ---------------- |
| `X-RateLimit-Limit`     | 制限値           |
| `X-RateLimit-Remaining` | 残りリクエスト数 |
| `X-RateLimit-Reset`     | リセット時刻     |

UUID形式のパスパラメータは正規化され、同一エンドポイントとして制限が適用される。

---

## 8. データモデル・DTO定義

### 8.1 DTO一覧

#### 8.1.1 AudioFormat (enum)

```python
class AudioFormat(str, Enum):
    WAV = "wav"
    OPUS = "opus"
    PCM = "pcm"
```

#### 8.1.2 TranscriptionResult

| フィールド    | 型    | 説明                     |
| ------------- | ----- | ------------------------ |
| `text`        | str   | 認識されたテキスト       |
| `confidence`  | float | 信頼度 (0.0-1.0)         |
| `language`    | str   | 検出言語 ("ja", "en" 等) |
| `duration_ms` | int   | 音声の長さ（ミリ秒）     |

#### 8.1.3 Speaker (enum)

```python
class Speaker(str, Enum):
    USER = "user"       # XRデバイス装着者
    PARTNER = "partner"  # 会話相手
```

#### 8.1.4 EmotionContext

| フィールド        | 型               | 説明         |
| ----------------- | ---------------- | ------------ |
| `primary_emotion` | str              | 主要な感情   |
| `emotion_scores`  | dict[str, float] | 全感情スコア |

#### 8.1.5 Utterance

| フィールド        | 型                     | 説明                  |
| ----------------- | ---------------------- | --------------------- |
| `speaker`         | Speaker                | 話者 (USER / PARTNER) |
| `text`            | str                    | 発話内容              |
| `timestamp`       | datetime               | タイムスタンプ        |
| `emotion_context` | EmotionContext \| None | 感情コンテキスト      |

#### 8.1.6 EmotionInterpretation

| フィールド        | 型          | 説明                             |
| ----------------- | ----------- | -------------------------------- |
| `primary_emotion` | str         | 主要な感情                       |
| `intensity`       | str         | 強度 ("low" / "medium" / "high") |
| `description`     | str         | 自然言語での説明                 |
| `suggestion`      | str \| None | 行動提案                         |

#### 8.1.7 EmotionChange

| フィールド     | 型  | 説明         |
| -------------- | --- | ------------ |
| `from_emotion` | str | 変化前の感情 |
| `to_emotion`   | str | 変化後の感情 |
| `description`  | str | 変化の説明   |

#### 8.1.8 ResponseSuggestion

| フィールド | 型  | 説明                                |
| ---------- | --- | ----------------------------------- |
| `text`     | str | 応答文                              |
| `intent`   | str | 意図（"共感を示す", "質問する" 等） |

#### 8.1.9 LLMResponseResult

| フィールド           | 型                       | 説明                                    |
| -------------------- | ------------------------ | --------------------------------------- |
| `responses`          | list[ResponseSuggestion] | 応答候補（2パターン、異なる意図が必須） |
| `situation_analysis` | str                      | 状況分析                                |

#### 8.1.10 AnalysisRequest

| フィールド       | 型               | デフォルト         | 説明                       |
| ---------------- | ---------------- | ------------------ | -------------------------- |
| `type`           | str              | "ANALYSIS_REQUEST" | メッセージタイプ           |
| `session_id`     | str              | （必須）           | セッションID               |
| `timestamp`      | datetime         | （必須）           | クライアントタイムスタンプ |
| `emotion_scores` | dict[str, float] | （必須）           | 感情スコア                 |
| `audio_data`     | str \| None      | None               | 音声データ（Base64）       |
| `audio_format`   | str \| None      | None               | 音声フォーマット           |

#### 8.1.11 AnalysisResponse

| フィールド           | 型                          | デフォルト          | 説明                   |
| -------------------- | --------------------------- | ------------------- | ---------------------- |
| `type`               | str                         | "ANALYSIS_RESPONSE" | メッセージタイプ       |
| `timestamp`          | datetime                    | （必須）            | サーバータイムスタンプ |
| `emotion`            | EmotionInterpretation       | （必須）            | 感情解釈               |
| `transcription`      | TranscriptionResult \| None | None                | STT結果                |
| `suggestions`        | list[ResponseSuggestion]    | （必須）            | 応答候補               |
| `situation_analysis` | str                         | （必須）            | 状況分析               |
| `processing_time_ms` | int                         | （必須）            | 処理時間               |

#### 8.1.12 CoachingResponse（内部用）

| フィールド         | 型        | 説明                   |
| ------------------ | --------- | ---------------------- |
| `advice`           | str       | 30文字以内のアドバイス |
| `strategy_tag`     | str       | 戦略タグ (#共感 など)  |
| `reply_candidates` | list[str] | 返答候補リスト         |
| `risk_score`       | int       | 危険度 (1-5)           |

### 8.2 ドメインモデル

#### 8.2.1 Session

| フィールド   | 型               | 説明                |
| ------------ | ---------------- | ------------------- |
| `id`         | str              | セッションID (UUID) |
| `user_id`    | str              | 所有者のユーザーID  |
| `status`     | str              | "active" / "ended"  |
| `started_at` | datetime         | 開始時刻 (UTC)      |
| `ended_at`   | datetime \| None | 終了時刻            |

#### 8.2.2 FeatureLog

| フィールド    | 型           | 説明          |
| ------------- | ------------ | ------------- |
| `id`          | str          | ログID (UUID) |
| `session_id`  | str          | セッションID  |
| `received_at` | datetime     | 受信時刻      |
| `facial`      | dict \| None | 顔特徴量      |
| `gaze`        | dict \| None | 視線特徴量    |
| `voice`       | dict \| None | 音声特徴量    |

---

## 9. サービス層仕様

### 9.1 STTサービス（STTService）

Google Cloud Speech-to-Text APIによる音声認識。

**メソッド**:

```python
async def transcribe(
    audio_data: bytes,
    format: AudioFormat,
    sample_rate: int = 16000,
    language: str = "ja",
) -> TranscriptionResult
```

**音声フォーマット対応**:

| フォーマット | エンコーディング |
| ------------ | ---------------- |
| WAV          | LINEAR16         |
| PCM          | LINEAR16         |
| OPUS         | OGG_OPUS         |

**言語コードマッピング**:

| 入力 | Google Cloud形式 |
| ---- | ---------------- |
| `ja` | `ja-JP`          |
| `en` | `en-US`          |
| `ko` | `ko-KR`          |
| `zh` | `zh-CN`          |
| `fr` | `fr-FR`          |
| `de` | `de-DE`          |
| `es` | `es-ES`          |
| `it` | `it-IT`          |
| `pt` | `pt-BR`          |

### 9.2 会話履歴管理サービス（ConversationService）

セッション内の会話コンテキストを管理し、LLM推論に必要な履歴を提供する。

**メソッド**:

| メソッド                                                                 | 説明                                |
| ------------------------------------------------------------------------ | ----------------------------------- |
| `add_utterance(session_id, speaker, text, emotion_context?, timestamp?)` | 発話を履歴に追加                    |
| `get_recent_context(session_id, max_turns=10)`                           | 直近の会話履歴を取得（古い順）      |
| `get_last_utterance(session_id, speaker?)`                               | 最後の発話を取得                    |
| `clear(session_id)`                                                      | セッションの履歴をクリア            |
| `get_conversation_summary(session_id)`                                   | 会話の要約を生成（LLMプロンプト用） |

**制約**:
- セッションあたり最大100件（デフォルト）
- 上限超過時はFIFO（古い履歴から削除）
- スレッドセーフ（`asyncio.Lock` 使用）
- インメモリストレージ（サーバー再起動で消失）

**会話要約フォーマット**:
```
=== 会話履歴 ===
[USER] こんにちは、今日の会議の件で相談があります。
[PARTNER] (困惑) はい、どのような相談でしょうか？
[USER] 明日の会議の時間を変更したいのですが...
```

### 9.3 感情解釈サービス（EmotionInterpreterService）

感情スコアを人間が理解できる自然言語に変換する。

**メソッド**:

| メソッド                                          | 説明                   |
| ------------------------------------------------- | ---------------------- |
| `interpret(emotion_scores)`                       | 感情スコアを解釈       |
| `detect_change(previous, current, threshold=0.3)` | 感情の急激な変化を検出 |

**強度判定基準**:

| 強度   | スコア範囲          |
| ------ | ------------------- |
| low    | 0.0 <= score < 0.4  |
| medium | 0.4 <= score < 0.7  |
| high   | 0.7 <= score <= 1.0 |

**感情別の日本語説明**:

| 感情      | low                          | medium                     | high                           |
| --------- | ---------------------------- | -------------------------- | ------------------------------ |
| happy     | 相手は少し嬉しそうです       | 相手は嬉しそうにしています | 相手はとても喜んでいます       |
| sad       | 相手は少し寂しそうです       | 相手は悲しんでいるようです | 相手はとても悲しんでいます     |
| angry     | 相手は少し不満がありそうです | 相手は怒っているようです   | 相手はとても怒っています       |
| surprised | 相手は少し驚いています       | 相手は驚いているようです   | 相手はとても驚いています       |
| confused  | 相手は少し戸惑っています     | 相手は困惑しているようです | 相手はとても困惑しています     |
| neutral   | 相手は落ち着いています       | 相手は平静な状態です       | 相手は無表情です               |
| fearful   | 相手は少し不安そうです       | 相手は怖がっているようです | 相手はとても怖がっています     |
| disgusted | 相手は少し不快そうです       | 相手は嫌悪感を示しています | 相手は強い嫌悪感を示しています |

**感情別の行動提案**:

| 感情      | 提案                                           |
| --------- | ---------------------------------------------- |
| happy     | この調子で会話を続けると良いでしょう           |
| sad       | 共感を示すと良いかもしれません                 |
| angry     | 一度話を整理して、相手の意見を聞いてみましょう |
| surprised | 追加の説明をすると良いかもしれません           |
| confused  | 説明を補足すると良いかもしれません             |
| neutral   | （提案なし）                                   |
| fearful   | 安心させる言葉をかけると良いでしょう           |
| disgusted | 話題を変えることを検討してください             |

**感情変化検出条件**:
1. 主要感情が変わった
2. かつ、変化量が閾値（デフォルト0.3）を超えている

### 9.4 LLMサービス（LLMService）

Gemini API（Vertex AI）による応答候補生成。

**メソッド**:

```python
async def generate_responses(
    conversation_context: list[Utterance],
    emotion_interpretation: EmotionInterpretation,
    partner_last_utterance: str,
) -> LLMResponseResult
```

**使用モデル**: Gemini 2.5 Flash (Fine-tuned) via Vertex AI (ChatVertexAI)

**システムプロンプト概要**:
- 対面コミュニケーション支援アシスタントとして振る舞う
- 会話履歴、感情状態、最後の発話を入力
- 2パターンの応答候補（異なる意図）と状況分析をJSON形式で出力
- 日本語で応答

**応答の意図（intent）例**:
- 話題を深める
- 共感を示す
- 質問する
- 話題を変える
- 確認する
- 提案する
- 励ます

**リトライ設定**:

| 項目             | 値     |
| ---------------- | ------ |
| 最大リトライ回数 | 3      |
| 初期遅延         | 1.0秒  |
| 指数ベース       | 2      |
| 最大遅延         | 10.0秒 |

**出力バリデーション**: Pydantic v2によるJSON構造化出力の検証。

### 9.5 応答生成サービス（ResponseGeneratorService）

全サービスを統合するオーケストレーター。

**処理パイプライン**:

```
1. 処理開始時刻を記録
       ↓
2. 音声データがある場合 → STTサービスでテキスト変換
   （失敗時はNone、処理は継続）
       ↓
3. 会話履歴に追加（テキストがある場合）
   speaker = PARTNER、感情コンテキスト付与
       ↓
4. 感情スコアを解釈
   EmotionInterpreterService.interpret()
       ↓
5. LLM推論
   会話履歴 + 感情解釈 + 最後の発話 → 2パターンの応答候補
       ↓
6. 結果統合
   AnalysisResponseを構築、処理時間を計算
```

**エラーハンドリング方針**:

| エラー種別       | 対応                                                    |
| ---------------- | ------------------------------------------------------- |
| STT失敗          | ログ出力、処理継続（transcription=None）                |
| 会話履歴更新失敗 | ログ出力、処理継続                                      |
| 感情解釈失敗     | デフォルト値（neutral）で継続                           |
| LLM失敗          | 例外を上位に伝播（WebSocketハンドラでエラーレスポンス） |

### 9.6 セッションサービス（SessionService）

セッションのライフサイクルを管理する。

**メソッド**:

| メソッド                            | 説明               |
| ----------------------------------- | ------------------ |
| `create(user_id)`                   | 新規セッション作成 |
| `end(session_id)`                   | セッション終了     |
| `verify_owner(session_id, user_id)` | 所有者検証         |

### 9.7 レート制限（InMemoryRateLimiter）

Sliding Windowアルゴリズムによるインメモリレート制限。

**特徴**:
- パスパターン別の制限値設定
- UUID正規化による統一的なパス処理
- ウィンドウ期限切れ時の自動クリーンアップ
- ユーザー分離（ユーザーID単位での制限）

### 9.8 接続管理（ConnectionManager）

WebSocket接続のライフサイクル管理。

**機能**:
- セッション単位の接続追跡
- ブロードキャスト送信
- ターゲット指定送信
- 切断時のクリーンアップ

### 9.9 AIエージェント（ERAAgent / LoveCoachAgent）

Core層のプロンプトとGemini APIを繋ぐエージェント。

**インターフェース**:
```python
class AgentInterface(ABC):
    @abstractmethod
    async def run(self, input_data: dict) -> dict: ...
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

# GCP認証（ADC）
# ローカル: gcloud auth application-default login
# Cloud Run: サービスアカウントから自動取得（設定不要）
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
| 2026-02-08 | #167 Canbasサイズ変更、ANALYSIS_RESPONSEの受信ログアンドロイドだけではなくunityで出力する。 | @93tajam |
| 2026-02-04 | #155 feat: #152 LLMServiceでFTモデル（LLMClientFactory）を使用 | @miyabi206 |
| 2026-02-03 | #145 #141 feat: ヘルスチェックにモデル接続状態を追加 | @miyabi206 |
| 2026-02-02 | #132 feat: 仕様書自動作成機能を追加 | @miyabi206 |

</details>

<details>
<summary>プログラム更新</summary>

| 日付       | 内容                         | 更新者 |
| ---------- | ---------------------------- | ------ |
| 2026-02-08 | #167 Canbasサイズ変更、ANALYSIS_RESPONSEの受信ログアンドロイドだけではなくunityで出力する。 | @93tajam |
| 2026-02-07 | #158 feat: e2eテストにあたっての必要な機能と修正 | @Daccho |
| 2026-02-04 | #155 feat: #152 LLMServiceでFTモデル（LLMClientFactory）を使用 | @miyabi206 |
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
- #152 feat: LLMServiceでFTモデル（LLMClientFactory）を使用する
- #142 docs: .env.example にFTモデル設定例を追記
- #141 feat: ヘルスチェックにモデル接続状態を追加
- #140 feat: LLM_TEMPERATURE を設定から変更可能にする
- #130 仕様書の自動更新機能
- #127 [Unity] メイン画面統合 - Android/XR共通UI実装
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
| #172 | [検証]サーバーのログが見づらい | @Daccho | ### 概要 |
| #171 | [MVP v2]フィードバック機能の追加 | - | ### 概要 |
| #169 | feat: 外カメラ映像 → 顔推論 → submitExt | @93tajam | ## 概要 |
| #168 | feat: Unity + unity-plugin 経路で | @93tajam | ## 概要 |
| #164 | [chore]<summy>のコメントが邪魔 | @Daccho | ### 概要 |
| #163 | gcp認証を統一 | @Daccho | ### 概要 |
| #162 | GCP認証のADC統一化 | @Daccho | ## 概要 |
| #161 | STTの脆弱性修正と仕様書更新 | - | ## 概要 |
| #160 | rules とか Cloude Opus 4.6対応 ※Pr | @miyabi206 | ### 概要 |
| #159 | perf: STTと感情解釈の並列化によるレスポンス時間短縮 | - | ## 概要 |
| #157 | feat(server): emotion_scoresバリ | - | ## 概要 |
| #156 | LoRAのやり直し | @miyabi206 | ### 概要 |
| #154 | SPECIFICATION.mdのコンフリクト防止 | @miyabi206 | ### 概要 |
| #151 | feat: 通常Android端末でのカメラパススルー（AR | @Daccho | ## 概要 |
| #150 | 仕様書をAGENTS.mdとCLOUD.mdに段階的表示する | @miyabi206 | ### 概要 |
| #149 | 仕様書をAGENT.mdやClaude.mdに段階的開示させ | @miyabi206 | - |
| #148 | [Unity] Quest/Android用ビルド自動化スク | @Daccho | ## 概要 |
| #147 | [Unity] 通常Android端末でXR自動初期化による | @Daccho | ## 概要 |
| #139 | feat: Vertex AIファインチューニング済みGem | @miyabi206 | ## 概要 |
| #129 | q | @Daccho | ### 概要 |
| #106 | env.exampleの作成 | - | ### 概要 |
| #98 | claude code skills, rules, age | @miyabi206 | 必要なskills, agentsなどに絞る |
| #70 | refactor: Speaker, Utterance,  | @Daccho | ## 概要 |
| #69 | [chore]projectsをみやするするための調整 | @Daccho | ### 概要 |
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
