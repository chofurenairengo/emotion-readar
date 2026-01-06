# 絶対読んで！
日本語で回答してください


# Repository Guidelines

## Project Structure & Module Organization
- `client/` contains frontend clients:
  - `client/Mitou/` is the Unity project (Assets, Packages, ProjectSettings).
  - `client/android/` is the native Android app (Gradle, Kotlin sources in `app/src/main`).
- `server/` is the FastAPI backend (entry `main.py`, config in `pyproject.toml`/`uv.lock`, Dockerfile).
- `docs/` holds architecture diagrams and notes.
- `docker-compose.yml` starts a local API + DynamoDB stack; update paths if the backend folder moves.

## Build, Test, and Development Commands
- Backend setup: `cd server` then `uv sync` (use `uv sync --group dev` for lint/test tools).
- Run API: `uv run uvicorn main:app --reload`.
- Lint/format: `uv run ruff check .` and `uv run black .`.
- Python tests: `uv run pytest`.
- Android: `cd client/android` then `./gradlew assembleDebug`, `./gradlew test`, `./gradlew connectedAndroidTest` (device/emulator required).
- Unity: open `client/Mitou` with the Unity editor.

## Coding Style & Naming Conventions
- Python: Black formatting (line length 88) and Ruff linting; use `snake_case` for functions/vars and `PascalCase` for classes.
- Kotlin/C#: follow standard IDE formatting; `camelCase` for functions/vars and `PascalCase` for types.
- Keep file and directory names descriptive; match existing module layout.

## Testing Guidelines
- Place Python tests alongside server code or under `server/` using pytest defaults (`test_*.py`).
- Android tests live in `client/android/app/src/test` (unit) and `client/android/app/src/androidTest` (instrumented).
- Add Unity tests via the Unity Test Runner when needed.

## Commit & Pull Request Guidelines
- Commits are short and descriptive; optional prefixes like `init:` appear in history. Keep messages one line.
- PRs should include a brief summary, how to run or verify changes, and screenshots for UI/UX changes (Unity/Android). Link related issues when available.

## Configuration & Secrets
- Local Docker uses `.env` values (AWS and DynamoDB settings). Do not commit secrets.
- Python version is pinned via `server/.python-version` (3.14).

## サーバーサイドのファイル構成

comm-xr-server/
├── app/
│   ├── main.py                  # ★アプリ起動点 (FastAPIインスタンス作成, Middleware適用, DI設定)
│   │
│   ├── core/                    # 【Layer 1: Foundation (ルール)】 
│   │   # ※ 依存：なし (標準ライブラリのみ)
│   │   ├── config.py            # 環境変数・定数 (Pydantic Settings)
│   │   ├── exceptions.py        # カスタム例外定義 (CommXRExceptionなど)
│   │   ├── prompts/             # プロンプト管理
│   │   │   └── love_coach.py    # システムプロンプトのテンプレート
│   │   └── interfaces/          # 抽象インターフェース (契約書)
│   │       ├── ai_client.py     # "AIクライアントの振る舞い"定義
│   │       └── db_repo.py       # "DBリポジトリの振る舞い"定義
│   │
│   ├── models/                  # 【Layer 1: Domain Entities (真実)】
│   │   # ※ 依存：なし
│   │   └── session.py           # DB保存用・内部ロジック用のデータ定義 (ID, CreatedAt含む)
│   │
│   ├── dto/                     # 【Layer 1: DTO (窓口)】 (旧Schemas)
│   │   # ※ 依存：なし
│   │   ├── request.py           # Androidからの入力型 (Text, Emotions)
│   │   └── response.py          # Androidへの出力型 (Reply, Strategy)
│   │
│   ├── services/                # 【Layer 2: Use Cases (脳みそ)】
│   │   # ※ 依存：Core, Models, DTO, Utils (Infra/APIは禁止)
│   │   ├── chat_orchestrator.py # 会話フローの制御、Model⇔DTO変換
│   │   ├── connection_manager.py # WebSocket接続管理
│   │   └── agents/              # AIエージェント (思考ロジック)
│   │       └── love_coach.py    # Coreのインターフェースを使って思考する
│   │
│   ├── infra/          # 【Layer 2: Frameworks & Drivers (手足)】
│   │   # ※ 依存：Core, Models
│   │   ├── external/
│   │   │   └── gemini_client.py # Geminiライブラリの実装
│   │   └── repositories/
│   │       └── dynamo_repo.py   # Boto3 (DynamoDB) の実装
│   │
│   ├── middleware/              # 【Layer 3: Middleware (門番)】
│   │   ├── logging.py           # パフォーマンス計測・ログ
│   │   └── cors.py              # CORS設定 (必要ならここに分離)
│   │
│   ├── api/                     # 【Layer 3: Interface Adapters (受付)】
│   │   # ※ 依存：Services, DTO
│   │   ├── dependencies.py      # ServiceにInfraを注入するDI設定
│   │   └── routers/
│   │       ├── chat.py          # WebSocket / POST エンドポイント
│   │       └── health.py        # ヘルスチェック
│   │
│   └── utils/                   # 【Helpers (便利屋)】
│       # ※ 依存：なし (純粋な関数)
│       ├── audio.py             # 音声データの変換処理
│       └── time.py              # 時間計算・JST変換
│
├── scripts/                     # 【Tools (工事用)】
│   # ※ サーバー起動には不要。開発者が手動で使う。
│   ├── setup_dynamodb.py        # ローカル/開発用テーブル作成
│   └── test_websocket.py        # 接続テスト用クライアント
│
├── tests/                       # テストコード
│   ├── unit/
│   └── integration/
│
├── .env                         # 環境変数 (APIキー等)
├── Dockerfile                   # 本番デプロイ用
├── docker-compose.yml           # ローカル開発用 (DynamoDB Local等)
├── pyproject.toml               # ★uv: プロジェクト設定・依存関係
├── uv.lock                      # ★uv: 依存関係のロックファイル
└── .python-version              # ★uv: Pythonバージョン指定 (3.11推奨)

クリーンアーキテクチャーを基盤にした３層ファイルアーキテクチャーで構成されている



・優先度低い

人格が統一される

→どんな自分になりたいかを最初に聞いて、プロンプトに組み込む。(遅延対策で文字数制限する)

## 仕様書
アーキテクチャは以下のようにする
┌────────────────────────── 現実世界 ──────────────────────────┐
│  相手 / 自分                                                   │
└───────────────┬───────────────────────────────┬──────────────┘
                │ カメラ                          │ マイク(複数ch) + センサー
                ▼                                ▼
┌──────────────────────── Android端末（エッジ） ────────────────────────┐
│  A) Androidネイティブ（Kotlin/C++）                                  │
│   ├─ 画像認識：MediaPipe Face/Pose Landmarker                         │
│   │    - 表情(Blendshape), 視線/頭部姿勢, 顔向き, 距離など            │
│   ├─ 音声認識（前処理）：VAD/音量/ピッチ/スペクトル                   │
│   ├─ (任意) 話者方向：DOA(マイクアレイ) + 画角内人物IDの照合          │
│   └─ (任意) 端末内STT：軽量STT（可能なら）                            │
│                                                                      │
│  B) Unity（C#）                                                      │
│   ├─ HUD/VFX：空気の可視化（粒子/色/揺らぎ）                          │
│   ├─ UI：返答候補2-3 / 会話戦略タグ / 注意点                          │
│   └─ 通信：WebSocket（リアルタイム） + HTTP（管理）                   │
│                                                                      │
│  データ方針：画像/生音声は基本「端末内で破棄」→送るのは特徴量のみ      │
└─────────────────────────┬────────────────────────────────────────────┘
                          │   送信（テキスト + 非言語特徴量）
                          ▼
┌──────────────────────────── AWS（クラウド） ───────────────────────────┐
│  入口：ALB                                                            │
│   ▼                                                                    │
│  ECS Fargate：FastAPI（セッション/統合/LLM制御）                        │
│   ├─ セッション管理（会話状態、直近N秒の文脈）                          │
│   ├─ 音声認識（STT）                                                     │
│   │    Option1: Amazon Transcribe（ストリーミングSTT）                  │
│   │    Option2: Faster-Whisper（ECS別サービス）※重いので後回し推奨      │
│   ├─ LLM：Gemini / OpenAI 等のAPI呼び出し                               │
│   │    - 入力：STTテキスト + 特徴量(表情/視線/声トーン/話者ID)          │
│   │    - 出力：候補返答2-3 + 戦略(共感/質問/深掘り/撤退) + 禁則          │
│   ├─ 返答の整形（短文化、トーン調整、禁止語フィルタ）                   │
│   └─ ログ/メトリクス（遅延p95、成功率）                                 │
│                                                                      │
│  永続化：                                                             │
│   ├─ Cognito：認証                                                     │
│   ├─ DynamoDB：セッション/特徴量時系列/評価指標                          │
│   ├─ S3：任意で音声断片/デモ素材（同意がある場合のみ）                  │
│   ├─ CloudWatch：監視・ログ                                             │
│   └─ Secrets Manager：APIキー等                                         │
└─────────────────────────┬────────────────────────────────────────────┘
                          │ 応答（リアルタイムpush）
                          ▼
┌──────────────────────── Android端末（Unity HUD） ──────────────────────┐
│  返答候補 / 戦略タグ / 注意点 → HUD表示（視界を邪魔しないMFUI）          │
└──────────────────────────────────────────────────────────────────────┘

以下の仕様書をもとにする

仕様書

作成：2025年12月23日

更新履歴



3の最後の文を追加(2025/12/25)

3.4に類似製品のURL貼った(2025/12/28)

最後の行に「課題と解決策」を追加(2025/12/28)

プロジェクト名：コミュXR（Comm-XR）

～3Dゲーム技術で「空気を可視化」し、対人能力を拡張するXRシステム～



1. プロジェクト概要

本プロジェクトは、眼鏡型XRデバイスを用い、対面コミュニケーションにおける言語（会話内容）と非言語（表情・視線・声のトーン）の情報をリアルタイムに解析・可視化することで、ユーザーの対人能力を拡張するシステムである。単なる「AIとの対話」に留まらず、現実の人間関係を豊かにするための「コミュニケーションの補助輪（Social Training Wheels）」となることを目指す。 1



2. 解決したい課題

会話の行き詰まり: 初対面や多人数での会話において、適切な話題や返答が見つからない。 2

非言語情報の見落とし: 相手の微細な表情の変化や、自分の振る舞い（笑顔の不足、視線の不一致）に気づけず、心理的距離を詰められない。 3

AI依存からの脱却: AIチャットとの会話で満足してしまい、現実世界での交流が疎かになる「AI閉じこもり」の防止。 4

3. 本プロジェクトの独創性

「補助輪」としての設計: AIに依存させるのではなく、最終的にはデバイスなしで自立できることをゴールとした教育的・訓練的アプローチ。 5

3Dゲーム技術の転用: 複雑な感情数値を「文字」ではなく、3Dパーティクルやシェーダーによる「空間演出」として描画。直感的な「空気の把握」を可能にする。 6

双方向コーチング: 相手の解析だけでなく、内向きカメラによる「自己の振る舞い」へのリアルタイムフィードバック（視線誘導、表情指示）の実装。 7

ドメイン特化型LLM: 「恋愛」や「初対面」など、本音と建前が入り混じる文脈に特化した機械学習・プロンプトエンジニアリング。 8

既存ツールとの比較

比較項目Apple Vision Pro / MS MeshコミュXR（本プロジェクト）主な用途仮想空間での作業・遠隔会議現実世界（対面）でのコーチング設計思想空間に没入させる（デジタル化）現実に適応させる（能力拡張）フィードバック感情の共有（アバター表示）感情の分析と戦略提示VFX演出リッチで没入感のある演出最小限で注意を削がない機能的UI社会親和性街中では不自然で威圧感がある眼鏡型で自然な会話が可能会話内容から質問された答えを生成するARグラス

https://www.makuake.com/project/halliday/?utm_source=kol_mrvr&utm_medium=youtube_videos&utm_campaign=makuake_influencer&utm_id=dec_kol_mrvr

紹介動画

https://m.youtube.com/watch?v=eVcD1Th9fRU

差別点:カメラで非言語解析→感情と会話内容、声色からより正確な返答。人間味のある親しみやすい返答を生成。選択肢の幅。フィードバック。

独創性のポイント: 既存ツールが「デジタル空間に人を引き込む」のに対し、コミュXRは「現実の人間関係を円滑にするための『透明な補助板』」であり、現実への進出を後押しする点にある。

テキストベースやAIアバターとの会話と違って「リアルで会話できる」勇気と経験を与えられる。

→「このメガネがあれば、コミュ障の自分もリアルで(異性と)会話できそう！」という気分



4. システムアーキテクチャ

Google AI Glass（2026年発売想定）を核とした、エッジ・クラウドハイブリッド構成を採用する。

コード スニペット

graph TD     A[Google AI Glass] -- "視覚(外/内カメ)・音声(4chマイク)" --> B[Android Smartphone]     B -- "① YOLOv11/MediaPipe: 顔・表情解析" --> B     B -- "② 話者分離/音源定位: 発話者特定" --> B     B -- "③ ストリーミング(gRPC)" --> C[Cloud: Gemini 1.5/2.0 Flash]     C -- "④ 恋愛特化型返答/アドバイス生成" --> B     B -- "⑤ VFX/HUD情報の空間配置計算" --> A     A -- "⑥ 視界への透過表示(Spatial Emoji)" --> D[ユーザー]     B -- "長期分析データの保存" --> E[Firebase/恋AI連携]



5. 技術スタック

1. フロントエンド（デバイス・描画層）

ユーザーの視界に直接干渉し、直感的なHUD（ヘッドアップディスプレイ）を提供する領域です。

項目技術・ツール備考開発エンジンUnity (2022.3 LTS+), C#VFX Graphを用いた高度な空間演出とマルチプラットフォーム対応。XR SDKAndroid XR SDK (Jetpack XR)2026年発売予定のGoogle AI Glassへの最適化。ARフレームワークAR Foundation / OpenXRグラスとスマホ、将来的な多機種展開を支える標準規格。空間UI設計Unity UI (UGUI) + Shader Graph透過型レンズでの視認性を追求した「最小機能UI（MFUI）」の実装。2. バックエンド（通信・制御層）

デバイス間の高速なデータ同期と、解析・推論処理のオフロードを担当します。

項目技術・ツール備考言語 / フレームワークPython (FastAPI)高並列処理と低遅延なAPIレスポンスの実現。通信プロトコルgRPC (Bidirectional Streaming)音声・映像バイナリデータの双方向・リアルタイム転送。音声解析エンジンFaster-Whisper会話のリアルタイムテキスト化（STT）。音源定位ロジックDOA (Direction of Arrival)マイクアレイを用いた話者の位置特定アルゴリズム。3. AI・API（解析・インテリジェンス層）

言語・非言語情報を統合し、コミュニケーションの「解」を生成します。

項目技術・ツール備考物体検知・追跡YOLOv11複数人のリアルタイム検知およびIDトラッキング。骨格・表情抽出MediaPipe Landmarker表情（Blendshape）および視線の微細な動きの数値化。対話生成モデルGemini 1.5 Flash or 2.0 Flash (API)コンテキスト理解と高速な返答生成。モデル最適化LoRA (Low-Rank Adaptation)恋愛・親睦ドメインに特化したファインチューニング。感情分析独自学習済みCNN / PADモデル非言語スコアを心理学的な感情ステータスへ変換。アイトラッキングは内カメラないとできない→ジャイロで代替(顔の向きを判定)



4. インフラ（基盤・保存層）

データの永続化と、スケーラブルな推論・分析環境を提供します。

項目技術・ツール備考クラウドプラットフォームGoogle Cloud (GCP)Gemini APIとの親和性および高速なネットワークインフラ。データベースCloud Firestore会話ログおよびユーザーの成長データのリアルタイム保存。リアルタイム同期Firebase Realtime Databaseデバイス間の解析ステータス同期。認証Firebase Authセキュアなユーザーログインとデータ保護。モノレポ管理pnpm開発環境管理（リポジトリの集約）。5. 開発環境（ツール・CI/CD）

効率的な開発と、未踏での機動力あるプロトタイピングを支えます。

項目技術・ツール備考エディタ / IDEUnity Editor / VS Code / Android StudioAndroid XRアプリ開発の標準セット。機械学習基盤PyTorch特化型モデルの学習およびLoRA調整用。バージョン管理Git / GitHubチーム開発およびソースコードの資産管理。デバッグ・プロファイラUnity Profiler / RenderDocグラス側の描画負荷と遅延（Latency）の最適化。旧技術スタック



6. 詳細開発スケジュール（2025年12月～2026年3月提出）

2025年12月：基盤構築

第4週（今週）：環境構築とAPI疎通Unityプロジェクト立ち上げ、Gemini APIおよびFaster-Whisper(STT)の組み込み。

第5週：基本ループの完成「音声入力 → テキスト化 → 返案2択生成 → スマホ画面表示」のMVP構築。

2026年1月：マルチモーダル解析の実装

第1週：自己解析（内カメ）MediaPipeを用いた自分の視線・表情解析とコーチングロジックの実装。

第2週：相手解析（外カメ）YOLOv11による複数人検知および表情スコアリングの統合。

第3週：話者分離とセンサーフュージョン音源定位（DOA）と視覚情報を照合し、「誰が喋っているか」を特定するロジックの実装。

第4週：デモ調整技育博に向けたUI/UXのブラッシュアップとデバッグ。

2026年2月：実証と申請準備

第1週：技育博での実演展示を通じたユーザーフィードバックの収集と課題の抽出。

第2週：ドメイン特化チューニングLoRAを用いた恋愛・親睦シーンへのLLM最適化。

第3週：空間UI（HUD）のモックアップAndroid XR上での視覚的通知（Emoji/ガイドバー）の配置最適化。

第4週：申請書類（JIS）起稿本仕様書をベースに、PM（稲見氏/米澤氏）向けの独創性を言語化。 19

2026年3月：未踏IT提出

第1週：デモ動画制作未踏審査の肝となる「解析ログ合成済み視点映像」の撮影・編集。

第2週：最終校正と提出3月中旬の締切に合わせ、すべての資料をアップロード。

7. 未踏申請に向けたメッセージ

本プロジェクトは、AIに頼り切る人間を作ることではなく、**『AIに背中を押してもらうことで、現実の人間関係に一歩踏み出す勇気を持つ人間』**を増やすための挑戦である。

リアルタイムな非言語解析（顔・視線・表情・話者位置） → クライアント（スマホ側）

会話の理解・返答案生成（LLM）や重い推論 → サーバー

個人情報が濃い画像（相手の顔）を送るのは最小化（基本送らない、送るなら特徴量だけ）

