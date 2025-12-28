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
- 音声をサーバーへ送信 → STT
- テキスト + 非言語特徴量を LLM へ入力
- 返答候補・戦略を Unity にプッシュ表示

## Edge Layer（Android Native）
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
│  ├─ unity/
│  └─ android-native/
├─ server/
│  ├─ api/
│  └─ worker/
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
