# 絶対読んで！
日本語で回答してください


# Repository Guidelines

## Project Structure & Module Organization
- `client/` contains frontend clients:
  - `client/Mitou/` is the Unity project (Assets, Packages, ProjectSettings).
  - `client/android/` is the native Android app (Gradle, Kotlin sources in `app/src/main`).
- `server/` is the FastAPI backend (entry `main.py`, config in `pyproject.toml`/`uv.lock`, Dockerfile).
- `docs/` holds architecture diagrams and notes.
- `docker-compose.yml` starts a local API + Cloud Functions stack; update paths if the backend folder moves.

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
- Local Docker uses `.env` values (Cloud Run, Cloud Functions settings). Do not commit secrets.
- Python version is pinned via `server/.python-version` (3.14).

## 詳細仕様（必要に応じて参照）

基本情報はこのファイルで十分です。より詳細な仕様が必要な場合のみ、以下を参照してください。

| 仕様 | ファイル | 参照タイミング |
|------|---------|--------------|
| 完全仕様書 | [SPECIFICATION.md](SPECIFICATION.md) | アーキテクチャ全体を理解する場合 |
| サーバー構成 | [docs/spec/server.md](docs/spec/server.md) | ディレクトリ構成、設定管理を確認する場合 |
| API仕様 | [docs/spec/api.md](docs/spec/api.md) | エンドポイント実装、WebSocket通信を実装する場合 |
| データモデル | [docs/spec/models.md](docs/spec/models.md) | DTO、ドメインモデルを実装する場合 |
| サービス層 | [docs/spec/services.md](docs/spec/services.md) | 各サービスの詳細実装を確認する場合 |

## 仕様書
アーキテクチャは以下のようにする

┌──────────────────────────────────────────────────────────────────────────┐
│                          【1. 現実世界 (Reality)】                       │
│                                                                          │
│  [ 相手A ] [ 相手B ] ...           <========>      [ ユーザー (自分) ]   │
│       │        │                                       ▲                 │
└───────┼────────┼───────────────────────────────────────┼─────────────────┘
        │光/音   │                                       │視覚 (HUD)
        ▼        ▼                                       │
┌─────────────────────────────────────────────────────────────────────────────┐
│                【2. エッジデバイス (Android / Google AI Glass)】             │
│                                                                             │
│  ┌─ [ A) Android Native (知覚・解析層) ] ─────────────────────────────────┐  │
│  │  ① 視覚解析 (MediaPipe / YOLO)                              　　　　   │  │
│  │     ・顔検出 & ID管理 (Person A, Person B...)                          │  │
│  │     ・基本表情 (笑顔、驚き) の瞬時判(あらかじめ定められた表情から最適な表情を選ぶ)   │  │
│  │  ② 聴覚解析 (Audio Processing)                                        │  │
│  │     ・VAD(発話検知) / 音量 / ピッチ                                    │  │
│  │     ・DOA(音源定位) → 「誰が話しているか」特定                          │  │
│  └─┬──────────────────────┬─────────────────────────────────────────────┘  │
│    │(1) 即時データ        │(2) ストリーミング送信 (音声+映像特徴量)          │
│    │    (Local)           │    (Serverへ)                                │
│    ▼                      ▼                                            │
│  ┌─ [ B) Unity (表示・通信層) ] ────────────────────────┐                │
│  │  ■ [即時フィードバック] (遅延 < 100ms)               │                │
│  │    ・基本感情絵文字 (happy/surprised/angry など) を顔の横に追従表示    │                │
│  │    ・話者ハイライト (現在話している人の枠を強調)     │                │
│  │    ※ 警告機能（早口・声量注意など）は実装しない      │                │
│  │                                                      │                │
│  │  ■ [AIアドバイス表示] (数秒〜十数秒に1回)            │                │
│  │    ・深層感情分析 (「退屈そう」「疑っている」)       │                │
│  │    ・会話戦略 / 回答候補 (2択)                       │                │
│  └───────────▲──────────────────────────────────────┘                │
└──────────────┼───────────────────────────────────────────────────────┘
               │
               │WS (JSON + Audio Binary)
               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    【3. クラウド (GCP)】                                  │
│                                                                          │
│  ┌─ [ ERA Server (FastAPI / Clean Architecture) ] ────────────────┐  │
│  │                                                                    │  │
│  │  << 1. 蓄積・制御 (Orchestrator) >>                                │  │
│  │   ・STT (文字起こし): Whisper等でテキスト化 (※内部理解用)          │  │
│  │   ・文脈バッファ: [文1, 文2, 文3, 文4, 文5]                        │  │
│  │      → 5文蓄積 or 長い沈黙検知 で LLM Trigger ON                  │  │
│  │                                                                    │  │
│  │  << 2. 思考・生成 (LLM Agent) >>                                   │  │
│  │   Input: { "fast emotion": [happy], "history": [5文],             │  │
│  │          "participants": [A, Bの特徴] }                            │  │
│  │   [ 思考プロセス ]                                                 │  │
│  │    ├─ Step 1: 高速RAG検索 (Tavily)                                 │  │
│  │    │     "会話中の不明単語" "最新ニュース" ──► [Web検索] ──► 情報  │  │
│  │    │                                                               │  │
│  │    ├─ Step 2: マルチモーダル深層分析                               │  │
│  │    │     Gemini 2.5 Flash Lite (Fine-tuned) を使用                 │  │
│  │    │     LoRA等で「恋愛/親睦」文脈に特化させたモデルで推論         │  │
│  │    │                                                               │  │
│  │    └─ Step 3: アドバイス生成                                       │  │
│  │           "検索事実" + "深層感情" + "文脈" ──► [選択肢案 x2]       │  │
│  │                                                                    │  │
│  │  << 3. 出力 (Response Builder) >>                                  │  │
│  │   Json形式で返却:                                                  │  │
│  │   {                                                                │  │
│  │     "deep_emotions": [{"id":"A", "emotion":"happy", "desc":"..."}, ...],│  │
│  │     "advice": {                                                             │
│  │       // 態度・行動への一言アドバイス                                │
│  │       "strategy": "視線を合わせ、ゆっくり頷いて",                     │
│  │                                                                              │
│  │       // 会話の選択肢（2択）                                                 │
│  │       "reply_options": [                                                     │
│  │         { "label": "共感", "text": "それは大変でしたね..." },                │
│  │         { "label": "深掘", "text": "具体的にどういうこと？" }                │
│  │       ]                                                                      │
│  │     }                                                              │  │
│  │   }                                                                │  │
│  └───────────┬────────────────────────────────────────────────────┘  │
│              │                                                        │
│              ▼                                                        │
│  ┌─ [ 外部連携API & DB ] ──────────────────────────────────────────┐   │
│  │  ・Gemini 2.5 Flash Lite (Fine-tuned)                          │   │
│  │  ・Tavily (Search)                                         │   │
│  │  ・Cloud Functions → Firestore (会話ログ・成長記録の保存)             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘




以下の仕様書をもとにする

仕様書

作成：2025年12月23日

プロジェクト名：Emotion ReadAR（E.R.A）

～3Dゲームと非言語フィードバックの技術で「感情を可視化」し、対人能力を拡張するXRシステム～


1. プロジェクト概要

本プロジェクトは、眼鏡型XRデバイスを用い、対面コミュニケーションにおける言語（会話内容）と非言語（表情・視線・声のトーン）の情報をリアルタイムに解析して、その情報をもとに次にユーザーが発言すべき選択肢をアドバイスと共に表示することで、ユーザーの対人能力を拡張するシステムである。単なる「AIとの対話」に留まらず、現実の人間関係を豊かにするための「コミュニケーションの補助輪（Social Training Wheels）」となることを目指す。


2. 解決したい課題

- **会話の行き詰まり:** 初対面や多人数での会話において、適切な話題や返答が見つからない。 
- **非言語情報の見落とし:** 相手の微細な表情の変化や、自分の振る舞い（笑顔の不足、視線の不一致）に気づけず、心理的距離を詰められない。 
- **AI依存からの脱却:** AIチャットとの会話で満足してしまい、現実世界での交流が疎かになる「AI閉じこもり」の防止。

3. 本プロジェクトの独創性

1. **「補助輪」としての設計:** AIに依存させるのではなく、最終的にはデバイスなしで自立できることをゴールとした教育的・訓練的アプローチ。 
2. **感情をシンプルに可視化:** 分析の結果導出した感情数値から感情のラベリングをして「文字」と「絵文字」で表現する。直感的な「感情の把握」を可能にする。 
3. **双方向コーチング:** 相手の解析だけでなく、内向きカメラによる「自己の振る舞い」へのリアルタイムフィードバック（視線誘導、表情指示）の実装。 
4. **ドメイン特化型LLM:** 「恋愛」や「初対面」など、本音と建前が入り混じる文脈に特化した機械学習・プロンプトエンジニアリング。 

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

## 1. アーキテクチャ概要

本システムは、「**エッジ・クラウド ハイブリッド構成**」を採用します。
レイテンシ（遅延）が致命的となる「非言語情報の抽出（画像解析・音声前処理）」をAndroidエッジ端末側で完結させ、計算リソースを要する「文脈理解・推論生成（LLM）」をクラウド側（Cloud Run）へオフロードすることで、**リアルタイム性と高度な推論の両立**を実現します。

また、サーバーサイドには**クリーンアーキテクチャ**を適用し、将来的なモデルの差し替えや機能拡張に耐えうる堅牢な設計とします。

---

## 2. クライアントサイド（Android端末 / Google AI Glass）

**役割：感覚器官（Sensing）と インターフェース（UI/UX）**

Android端末を演算コアとし、Glassをディスプレイおよび入力デバイスとして機能させます。UnityとAndroidネイティブ（Kotlin/C++）の連携により実装します。

### A) センシング・解析層 (Android Native / Kotlin & C++)

プライバシー保護と通信量削減のため、生データ（画像）は原則端末内で処理し、**「特徴量」のみ**をクラウドへ送信します。

- **画像認識 (MediaPipe):**
    - Blendshapeを用いて、相手の表情、視線、頭部の向きを数値化。
    - Pose Landmarkerにより、身体の姿勢やジェスチャーを解析。
- **音声認識前処理:**
    - **VAD (Voice Activity Detection):** 発話区間のみを切り出し。
    - **DOA (Direction of Arrival):** マイクアレイを用いて音源方向を特定し、画角内の人物IDと照合（誰が話したかを特定）。

### B) アプリケーション層 (Unity / C#)

ユーザー体験を司るHUD（Head-Up Display）の制御と、通信のハンドリングを行います。

- **VFX/HUD:**
    - 「空気の可視化」として、解析された感情値に応じたパーティクルや色相の変化をGlass上にオーバーレイ表示。
    - **MFUI (Minimal Functional UI):** 視界を遮らないよう、返答候補や会話戦略タグ（「共感」「深掘り」など）をシンプルに表示。
- **通信:**
    - WebSocketを使用し、バイナリストリーム（音声）とJSON（特徴量）をリアルタイムにサーバーへ送信。

---

## 3. サーバーサイド（Cloud Run）

**役割：頭脳（Brain）と 記憶（Memory）**

FastAPI on Cloud Run を基盤とし、Googleの**Gemini 2.5 Flash Liteのファインチューニング済みモデル**を推論エンジンとして採用します。

### ソフトウェアアーキテクチャ：クリーンアーキテクチャ

ディレクトリ構成に基づき、関心事を以下の層に分離して実装します。

1. **Core Layer (Foundation & Rules):**
    - システム全体で共有される設定 (`config.py`)、例外定義、およびドメインの「契約」となるインターフェース (`interfaces/`) を定義。
    - プロンプトエンジニアリング (`prompts/love_coach.py`) はここに集約し、AIの「人格」や「コーチング方針」を管理。
2. **Model Layer (Domain Entities):**
    - `session.py` など、システムの中心となるデータ構造を定義。外部ライブラリに依存しない純粋なデータクラス。
3. **Service Layer (Use Cases / Brain):**
    - **Chat Orchestrator:** 会話フローの制御中枢。STT結果、非言語特徴量、過去の文脈を統合し、AIエージェントへ指示を出す。
    - **Agents (Love Coach):** 具体的なアドバイス生成ロジック。Core層のプロンプトとGemini APIを繋ぐ。
4. **Interface Adapters (API & Infra):**
    - `api/routers/chat.py`: WebSocketエンドポイントを提供し、クライアントとの接続を確立。
    - `infra/external/gemini_client.py`: 実際にGemini APIを叩く実装詳細。
    - `infra/repositories/firestore_repo.py`: 会話ログやセッション情報の永続化を担当。

---

## 4. データフロー (Information Pipeline)

1. **入力:** 現実世界の「映像」と「音声」をGlass/スマホが取得。
2. **エッジ解析 (Android):**
    - 画像 → MediaPipeで「表情スコア」「視線データ」へ変換。
    - 音声 → VADで切り出し、特徴量と共に送信。
3. **転送:** WebSocket経由でテキスト＋非言語特徴量をCloud Runへ送信。
4. **推論 (Cloud):**
    - **STT:** 音声データをテキスト化（Google Cloud Speech-to-Text）。
    - **LLM (Gemini):** 「テキスト」＋「非言語情報を言語化した情報（相手が怒っている、笑っている等）」＋「文脈」を入力。
    - **出力:** 次の返答候補（2個）、会話戦略、アドバイスを生成。
5. **フィードバック:** 生成結果を即座にAndroidへPush。
6. **表示:** UnityがHUD上に情報を描画し、ユーザーの視界を拡張する。

| **領域** | **技術要素** | **選定理由** |
| --- | --- | --- |
| **Edge Logic** | **Kotlin / C++ (MediaPipe)** | 低遅延・高負荷な画像処理をネイティブ層で処理するため。 |
| **Edge UI** | **Unity (C#)** | 3D空間表現(VFX)とクロスプラットフォーム対応のため。 |
| **Backend API** | **FastAPI (Python)** | 非同期処理に強く、AIライブラリ（Python製）との親和性が高いため。 |
| **AI Model** | **Gemini 2.5 Flash** | マルチモーダル入力への対応と、リアルタイム対話に耐える応答速度。 |
| **Database** | **Cloud Firestore** | セッション管理など、高速なRead/Writeとスケーラビリティのため。 |
| **Architecture** | **Clean Architecture** | テスト容易性と、将来的なモジュール（STTエンジン等）の置換を容易にするため。 |

5. 技術スタック

5.1 フロントエンド（デバイス・描画層）

ユーザーの視界に直接干渉し、直感的なHUD（ヘッドアップディスプレイ）を提供する領域です。

項目技術・ツール備考開発エンジンUnity (2022.3 LTS+), C#VFX Graphを用いた高度な空間演出とマルチプラットフォーム対応。XR SDKAndroid XR SDK (Jetpack XR)2026年発売予定のGoogle AI Glassへの最適化。ARフレームワークAR Foundation / OpenXRグラスとスマホ、将来的な多機種展開を支える標準規格。空間UI設計Unity UI (UGUI) + Shader Graph透過型レンズでの視認性を追求した「最小機能UI（MFUI）」の実装。

5.2 バックエンド（通信・制御層）

デバイス間の高速なデータ同期と、解析・推論処理のオフロードを担当します。

項目技術・ツール備考言語 / フレームワークPython (FastAPI)高並列処理と低遅延なAPIレスポンスの実現。通信プロトコルgRPC (Bidirectional Streaming)音声・映像バイナリデータの双方向・リアルタイム転送。音声解析エンジンFaster-Whisper会話のリアルタイムテキスト化（STT）。音源定位ロジックDOA (Direction of Arrival)マイクアレイを用いた話者の位置特定アルゴリズム。

5.3 AI・API（解析・インテリジェンス層）

言語・非言語情報を統合し、コミュニケーションの「解」を生成します。

項目技術・ツール備考物体検知・追跡YOLOv11複数人のリアルタイム検知およびIDトラッキング。骨格・表情抽出MediaPipe Landmarker表情（Blendshape）および視線の微細な動きの数値化。対話生成モデルGemini 1.5 Flash or 2.0 Flash (API)コンテキスト理解と高速な返答生成。モデル最適化LoRA (Low-Rank Adaptation)恋愛・親睦ドメインに特化したファインチューニング。感情分析独自学習済みCNN / PADモデル非言語スコアを心理学的な感情ステータスへ変換。アイトラッキングは内カメラないとできない→ジャイロで代替(顔の向きを判定)

5.4 インフラ（基盤・保存層）

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

│   │       └── firestore_repo.py   # Firebase Admin SDK (Firestore) の実装

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

│   ├── setup_cloud_functions.py        # ローカル/開発用セットアップ

│   └── test_websocket.py        # 接続テスト用クライアント

│

├── tests/                       # テストコード

│   ├── unit/

│   └── integration/

│

├── .env                         # 環境変数 (APIキー等)

├── Dockerfile                   # 本番デプロイ用

├── docker-compose.yml           # ローカル開発用 (Firestore Emulator等)

├── pyproject.toml               # ★uv: プロジェクト設定・依存関係

├── uv.lock                      # ★uv: 依存関係のロックファイル

└── .python-version              # ★uv: Pythonバージョン指定 (3.14推奨)



クリーンアーキテクチャーを基盤にした３層ファイルアーキテクチャーで構成されている

appフォルダの直下にのみ__init__.pyファイルを置く。それ以外はいらない

## 優先度低い

人格が統一されるという問題
→どんな自分になりたいかを最初に聞いて、プロンプトに組み込む。(遅延対策で文字数制限する)

ローカルLLM(SLM)を使って即時応答とAgentやらせる