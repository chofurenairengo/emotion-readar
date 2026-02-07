# Meta Quest 3 手動E2Eテスト手順書

## 概要

Quest 3のパススルーカメラ・マイクからサーバーへのANALYSIS_REQUEST送信までの
エンドツーエンドテストを手動で実施するためのチェックリスト。

---

## 前提条件

- [ ] Meta Quest 3 実機
- [ ] Quest 3 の開発者モードが有効
- [ ] USB-C ケーブルでPCに接続済み（ADB接続確認: `adb devices`）
- [ ] サーバーが起動している（`docker compose up -d` (推奨)　または `uv run uvicorn main:app --reload`）
- [ ] Quest 3 からサーバーにネットワーク接続可能（同一LAN）
- [ ] アプリがQuest 3にインストール済み（`adb install app-debug.apk`）

### サーバー起動確認

```bash
# サーバーのIPアドレスを確認
ifconfig | grep "inet " | grep -v 127.0.0.1

# ヘルスチェック（Quest 3からアクセスできるIPで確認）
curl http://<SERVER_IP>:8000/api/health
```

---

## テスト1: カメラ → emotion_scores → サーバー送信

### 目的

Quest 3パススルーカメラで相手の顔を認識し、emotion_scoresがサーバーに正しく送信されることを確認する。

### 手順

1. [ ] Quest 3でアプリを起動する
2. [ ] セッション開始ボタンを押す（WebSocket接続が確立される）
3. [ ] Quest 3を装着し、**正面に人の顔がある状態**にする
4. [ ] サーバーログで `ANALYSIS_REQUEST` の受信を確認する

### 確認コマンド（サーバー側）

```bash
# サーバーログをリアルタイムで確認
docker compose logs -f api

# または直接実行時
# ログに "Received message type: AnalysisRequest" が表示される
```

### 検証チェックリスト

- [ ] サーバーが `ANALYSIS_REQUEST` を受信した
- [ ] `emotion_scores` に8つのキーが存在する（happy, sad, angry, confused, surprised, neutral, fearful, disgusted）
- [ ] 各スコアが 0.0〜1.0 の範囲内
- [ ] 笑顔を向けると `happy` スコアが高くなる
- [ ] 無表情だと `neutral` スコアが高くなる
- [ ] `session_id` と `timestamp` が正しく含まれている

### エッジケース

- [ ] 顔がない状態（壁を向く）→ blendshapesが空、リクエスト送信されないことを確認
- [ ] 顔が部分的に隠れた状態 → MediaPipeが検出できるか確認
- [ ] 暗い環境 → パススルーカメラの性能限界を確認

---

## テスト2: マイク → base64音声データ → サーバー送信

### 目的

Quest 3のマイクで録音した音声がbase64 WAV形式でサーバーに正しく送信されることを確認する。

### 手順

1. [ ] Quest 3でアプリを起動し、セッションを開始する
2. [ ] マイクの権限を許可する
3. [ ] **話しかける**（音声モニタリングが開始され、VADで音声検出→録音開始）
4. [ ] 話し終わる（1.5秒の無音で自動停止）
5. [ ] サーバーログで `audio_data` を含む `ANALYSIS_REQUEST` の受信を確認する

### 検証チェックリスト

- [ ] サーバーが `ANALYSIS_REQUEST` を受信した
- [ ] `audio_data` フィールドが非空のBase64文字列
- [ ] `audio_format` が `"wav"`
- [ ] サーバーがSTT（音声認識）で正しくテキストに変換できた
- [ ] `ANALYSIS_RESPONSE` に `transcription.text` が含まれている

### エッジケース

- [ ] 無音状態（何も話さない）→ 録音が開始されないことを確認
- [ ] 30秒以上話し続ける → 最大録音時間（30秒）で自動停止することを確認
- [ ] 非常に小さい声 → VAD閾値（500）を超えないため録音されないことを確認

---

## テスト3: 統合テスト（カメラ + 音声 同時送信）

### 目的

emotion_scoresとaudio_dataの両方を含むANALYSIS_REQUESTが正しく送信され、
サーバーから適切なANALYSIS_RESPONSEが返ることを確認する。

### 手順

1. [ ] Quest 3でアプリを起動し、セッションを開始する
2. [ ] 相手の顔が見える状態で会話する
3. [ ] サーバーログで受信内容を確認する

### 検証チェックリスト

- [ ] `ANALYSIS_REQUEST` に `emotion_scores` と `audio_data` の両方が含まれている
- [ ] サーバーが `ANALYSIS_RESPONSE` を返した
- [ ] レスポンスに以下が含まれている:
  - [ ] `emotion` （感情解釈: primary_emotion, intensity, description）
  - [ ] `transcription` （音声認識結果: text, confidence）
  - [ ] `suggestions` （返答候補: 2件）
  - [ ] `situation_analysis` （状況分析テキスト）
- [ ] Quest 3のHUD（Unity）にレスポンスが表示された

---

## テスト4: 接続の安定性

### WebSocket再接続

- [ ] サーバーを停止 → 再起動 → Quest 3が自動再接続することを確認
- [ ] WiFiを一時的にOFF → ON → 再接続（指数バックオフ: 1s→2s→4s→8s→16s→32s）
- [ ] 5回連続失敗後にエラー状態になることを確認

### ハートビート

- [ ] 30秒間隔でPING/PONGが交換されていることをサーバーログで確認
- [ ] 60秒間PONGが返らない場合に再接続が発生することを確認

---

## テスト5: パフォーマンス

- [ ] emotion_scoresの送信レートが安定している（約30fps目標）
- [ ] 音声録音中にカメラ認識が停止しないことを確認
- [ ] 長時間（5分以上）の連続使用でメモリリークがないことを確認（`adb shell dumpsys meminfo <package>`）
- [ ] サーバーレスポンスの処理時間が `processing_time_ms` で確認できる

---

## デバッグ用コマンド

```bash
# Quest 3のログをリアルタイムで確認
adb logcat | grep -E "(WebSocketClient|AudioRecorder|FaceLandmarkerHelper|EmotionScore)"

# WebSocket通信のみフィルタ
adb logcat -s WebSocketClient

# 音声録音のログ
adb logcat -s AudioRecorder

# カメラ・顔検出のログ
adb logcat -s FaceLandmarkerHelper

# サーバーへのネットワーク接続確認
adb shell ping <SERVER_IP>
```

---

## テスト結果記録テンプレート

| テスト項目                  | 結果        | 備考 |
| --------------------------- | ----------- | ---- |
| カメラ → emotion_scores送信 | PASS / FAIL |      |
| 笑顔でhappyスコア上昇       | PASS / FAIL |      |
| マイク → base64 WAV送信     | PASS / FAIL |      |
| STTテキスト変換             | PASS / FAIL |      |
| 統合（カメラ+音声）送信     | PASS / FAIL |      |
| ANALYSIS_RESPONSE受信       | PASS / FAIL |      |
| HUD表示                     | PASS / FAIL |      |
| WebSocket再接続             | PASS / FAIL |      |
| 長時間安定性                | PASS / FAIL |      |
