# API仕様

> **Note**: このファイルは [SPECIFICATION.md](../../SPECIFICATION.md) の7章を分割したものです。

---

## 7.1 REST API

### 7.1.1 ヘルスチェック

```
GET /api/health
```

**レスポンス** (200 OK):
```json
{
  "status": "ok"
}
```

### 7.1.2 セッション作成

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

### 7.1.3 セッション取得

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

### 7.1.4 セッション終了

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

### 7.1.5 特徴量受信

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

## 7.2 WebSocket API

### 7.2.1 接続

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

### 7.2.2 メッセージタイプ一覧

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

### 7.2.3 PING / PONG

```json
// クライアント → サーバー
{ "type": "PING" }

// サーバー → クライアント
{ "type": "PONG", "timestamp": "2024-01-01T12:00:00Z" }
```

### 7.2.4 RESET / RESET_ACK

```json
// クライアント → サーバー
{ "type": "RESET" }

// サーバー → クライアント
{ "type": "RESET_ACK", "timestamp": "2024-01-01T12:00:00Z" }
```

### 7.2.5 ERROR_REPORT / ERROR_ACK

```json
// クライアント → サーバー
{ "type": "ERROR_REPORT", "message": "エラー詳細" }

// サーバー → クライアント
{ "type": "ERROR_ACK", "timestamp": "2024-01-01T12:00:00Z" }
```

### 7.2.6 ANALYSIS_REQUEST / ANALYSIS_RESPONSE

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

### 7.2.7 ERROR

```json
{
  "type": "ERROR",
  "message": "Invalid session",
  "detail": null,
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### 7.2.8 WebSocketエラーメッセージ一覧

| エラーメッセージ               | 説明                     |
| ------------------------------ | ------------------------ |
| `Invalid JSON`                 | JSONパースエラー         |
| `Unsupported message type`     | 未対応のメッセージタイプ |
| `Missing required fields: ...` | 必須フィールド不足       |
| `Service not available`        | サービス未実装           |
| `Analysis failed: ...`         | 解析処理エラー           |

### 7.2.9 接続シーケンス

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

## 7.3 HTTPエラーレスポンス

| ステータス            | 説明                              |
| --------------------- | --------------------------------- |
| 404 Not Found         | セッション/リソースが見つからない |
| 429 Too Many Requests | レート制限超過                    |

## 7.4 レート制限

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
