# サーバーサイド仕様

> **Note**: このファイルは [SPECIFICATION.md](../../SPECIFICATION.md) の6章を分割したものです。

---

## 6.1 ディレクトリ構成

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

## 6.2 設定管理（config.py）

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
| `GOOGLE_APPLICATION_CREDENTIALS` | str | -                 | サービスアカウントキーパス         |

## 6.3 例外階層

```
ERAException (ベース例外)
├── STTError              # 音声認識エラー
├── LLMError              # LLM推論エラー
│   ├── LLMRateLimitError # レートリミットエラー
│   └── LLMResponseParseError # レスポンスパースエラー
├── SessionPermissionError # セッション権限エラー
└── ...
```
