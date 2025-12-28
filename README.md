#　 Comm-XR Design Document

（画像認識・音声認識・LLM 統合 XR システム）

1. Purpose / Goals（目的）

Comm-XR は、対面コミュニケーションにおける 非言語情報（表情・視線・声のトーン）と言語情報（会話内容）をリアルタイムに解析・可視化 し、
ユーザーの対人能力を拡張する XR システムである。

本設計の目的は以下である：

低遅延でのリアルタイムフィードバック

プライバシー配慮（顔画像・生音声の外部送信を最小化）

拡張可能なアーキテクチャ（将来的なモデル変更・精度向上）

MVP から本番まで耐える構成

2. High-Level Architecture（全体構成）
   レイヤー分割

本システムは 3 層構造を採用する。

Edge Layer（端末）
感覚入力と即時解析を担当

Experience Layer（Unity）
表示・UX・XR 表現を担当

Cloud Intelligence Layer（AWS）
音声認識・LLM 推論・状態管理を担当

3. System Flow（データフロー概要）

カメラ・マイク・センサーから入力取得

Android ネイティブで非言語特徴量をリアルタイム抽出

Unity で HUD / VFX 表示

音声をサーバーへ送信 → STT

テキスト + 非言語特徴量を LLM に入力

返答候補・戦略を Unity にプッシュ表示

4. Edge Layer（Android Native）
   4.1 技術選定

言語：Kotlin

理由：

MediaPipe 公式対応

低レイテンシ

Unity 連携が容易

4.2 画像認識（MediaPipe）

処理内容

顔検出

ランドマーク推定（最大 468 点）

表情（Blendshape）

視線・頭部姿勢推定

人物追跡（ID 維持）

出力

数値化された特徴量のみ

生画像は即時破棄

{
"face_id": 1,
"smile": 0.72,
"gaze_vector": [0.1, -0.2, 0.9],
"head_pose": { "yaw": 5.2, "pitch": -1.3 }
}

4.3 音声前処理

端末では 軽量処理のみ を行う。

VAD（発話開始/終了）

音量

ピッチ

話速（推定）

STT は行わない。

5. Experience Layer（Unity）
   5.1 役割

XR 描画

HUD 表示

空気の可視化（粒子・色・揺らぎ）

ユーザー操作の最小 UI

5.2 Unity が扱うデータ

Android ネイティブからの非言語特徴量

サーバーからの返答候補・戦略タグ

Unity は 解析を行わず、意味付け・可視化に専念する。

6. Cloud Intelligence Layer（AWS）
   6.1 インフラ

AWS

ALB

ECS Fargate

DynamoDB

S3（任意）

Cognito

CloudWatch

Secrets Manager

6.2 API Server

フレームワーク：FastAPI

Docker 管理

WebSocket + HTTP

6.3 音声認識（STT）

基本方針：サーバー処理

実装候補：

Amazon Transcribe（Streaming / 非 Streaming）

将来的に Whisper 系へ差し替え可能

6.4 LLM 処理

入力：

STT テキスト

非言語特徴量

会話コンテキスト

出力：

返答候補（2〜3）

会話戦略タグ（共感 / 質問 / 深掘り / 撤退）

注意喚起（任意）

{
"candidates": [
"それ面白いですね、もう少し教えてもらえますか？",
"最近それにハマった理由は何ですか？"
],
"strategy": "ask_open_question"
}

7. Data & Privacy Policy（設計上の重要方針）

顔画像・映像はクラウドに送信しない

生音声は保存しない（同意時のみ例外）

送信データは特徴量とテキストのみ

通信は TLS

秘密情報は Secrets Manager で管理

8. Repository Structure（モノレポ）
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

9. Non-Goals（やらないこと）

端末での高精度 STT

顔画像の長期保存

感情の断定（「怒っている」と断言しない）

フル自動会話（人が話す前提）

10. Future Extensions（将来拡張）

話者分離・方向推定の高度化

マルチユーザー対応

成長指標の長期可視化

オフラインモード（限定機能）
