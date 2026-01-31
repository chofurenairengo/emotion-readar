# API リファレンス

> 関連: [Issue #30 - サーバーサイドMVP実装](https://github.com/chofurenairengo/emotion-readar/issues/30)

ERA サーバーとクライアント間の通信仕様です。

## 全体アーキテクチャ

```
[Kotlin/Android]
    │
    ├─ 感情スコア（MediaPipe処理後）
    ├─ 音声データ（raw/圧縮）
    │
    ▼ WebSocket
┌─────────────────────────────────────────────────┐
│                    Server                        │
│                                                  │
│  ┌─────────────┐    ┌─────────────┐             │
│  │ STTサービス  │───▶│ 会話履歴    │             │
│  └─────────────┘    │ 管理        │             │
│                      └──────┬──────┘             │
│                             │                    │
│  ┌─────────────┐            │                    │
│  │ 感情解釈    │────────────┤                    │
│  │ サービス    │            │                    │
│  └─────────────┘            ▼                    │
│                      ┌─────────────┐             │
│                      │ LLMサービス │             │
│                      └──────┬──────┘             │
│                             │                    │
│                             ▼                    │
│                      ┌─────────────┐             │
│                      │ 応答生成    │             │
│                      │ サービス    │             │
│                      └──────┬──────┘             │
└─────────────────────────────┼────────────────────┘
                              │
                              ▼ WebSocket
                        [Unity/HUD]
```

---

## REST API

### 1. ヘルスチェック

**GET `/api/health`**

サーバーの稼働状況を確認します。

```json
// レスポンス (200 OK)
{
  "status": "ok"
}
```

---

### 2. セッション管理

#### セッション作成

**POST `/api/sessions`**

新しいセッションを開始します。

```json
// リクエストボディ: なし

// レスポンス (201 Created)
{
  "id": "abc123",
  "status": "active",
  "started_at": "2024-01-01T12:00:00Z",
  "ended_at": null
}
```

#### セッション取得

**GET `/api/sessions/{session_id}`**

セッション情報を取得します。

```json
// レスポンス (200 OK)
{
  "id": "abc123",
  "status": "active",
  "started_at": "2024-01-01T12:00:00Z",
  "ended_at": null
}

// エラーレスポンス (404 Not Found)
{
  "detail": "Not found"
}
```

#### セッション終了

**POST `/api/sessions/{session_id}/end`**

セッションを終了します。

```json
// リクエストボディ: なし

// レスポンス (200 OK)
{
  "id": "abc123",
  "status": "ended",
  "started_at": "2024-01-01T12:00:00Z",
  "ended_at": "2024-01-01T13:00:00Z"
}

// エラーレスポンス (404 Not Found)
{
  "detail": "Not found"
}
```

---

### 3. 特徴量受信

**POST `/api/features`**

Android クライアントから非言語特徴量を受信します。

```json
// リクエスト
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

// レスポンス (202 Accepted)
{
  "id": "log123",
  "session_id": "abc123",
  "received_at": "2024-01-01T12:00:00Z",
  "status": "accepted"
}

// エラーレスポンス (404 Not Found)
{
  "detail": "Session not found"
}
```

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `session_id` | string \| null | セッションID |
| `timestamp` | string \| null | クライアントタイムスタンプ (ISO 8601) |
| `facial` | object \| null | 顔特徴量 (キー: 特徴名, 値: 0.0-1.0) |
| `gaze` | object \| null | 視線特徴量 |
| `voice` | object \| null | 音声特徴量 |
| `extras` | object \| null | その他の任意データ |

---

## WebSocket API

### 接続

**WebSocket `/api/realtime?session_id={session_id}`**

リアルタイム双方向通信用の WebSocket 接続です。

| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| `session_id` | string | ✓ | セッションID |

---

### メッセージタイプ

#### PING / PONG

接続確認用のハートビートです。

```json
// クライアント → サーバー
{
  "type": "PING"
}

// サーバー → クライアント
{
  "type": "PONG",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### RESET / RESET_ACK

セッションのリセットを要求します。

```json
// クライアント → サーバー
{
  "type": "RESET"
}

// サーバー → クライアント
{
  "type": "RESET_ACK",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### ERROR_REPORT / ERROR_ACK

クライアントからのエラー報告です。

```json
// クライアント → サーバー
{
  "type": "ERROR_REPORT",
  "message": "エラー詳細"
}

// サーバー → クライアント
{
  "type": "ERROR_ACK",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### ANALYSIS_REQUEST / ANALYSIS_RESPONSE

MediaPipeで算出された感情スコアと音声データを受け取り、STT処理後にLLMで推論を行い、応答文2パターンと感情の言語化を返します。

```json
// クライアント → サーバー
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

// サーバー → クライアント
{
  "type": "ANALYSIS_RESPONSE",
  "timestamp": "2024-01-01T12:00:00Z",
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

| フィールド | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| `session_id` | string | ✓ | セッションID |
| `timestamp` | string | ✓ | クライアントタイムスタンプ (ISO 8601) |
| `emotion_scores` | object | ✓ | MediaPipeで算出された感情スコア |
| `audio_data` | string \| null | | Base64エンコードされた音声データ |
| `audio_format` | string \| null | | 音声フォーマット ("opus", "wav", "pcm") |
| `emotion` | object | ✓ | 感情解釈結果（感情の言語化） |
| `emotion.primary_emotion` | string | ✓ | 主要感情 |
| `emotion.intensity` | string | ✓ | 強度 ("low", "medium", "high") |
| `emotion.description` | string | ✓ | 自然言語での説明 |
| `emotion.suggestion` | string \| null | | 行動提案 |
| `transcription` | object \| null | | STT（音声認識）結果 |
| `suggestions` | array | ✓ | LLMによる応答候補（2パターン） |
| `situation_analysis` | string | ✓ | 状況分析 |
| `processing_time_ms` | int | ✓ | 処理時間（ミリ秒） |

#### ERROR

サーバーからのエラー通知です。

```json
// サーバー → クライアント
{
  "type": "ERROR",
  "message": "Invalid session",
  "detail": null,
  "timestamp": "2024-01-01T12:00:00Z"
}
```

---

## エラーレスポンス

### HTTP エラー

| ステータス | 説明 |
|-----------|------|
| 404 Not Found | セッション/リソースが見つからない |

### WebSocket エラー

`type: "ERROR"` のメッセージで通知されます。

| エラーメッセージ | 説明 |
|-----------------|------|
| Invalid JSON | JSON パースエラー |
| Unsupported message type | 未対応のメッセージタイプ |
| Missing required fields: ... | 必須フィールド不足 |
| Service not available | サービス未実装 |
| Analysis failed: ... | 解析処理エラー |

---

## DTO 一覧

### HealthResponse

```typescript
{
  status: string;  // "ok"
}
```

### SessionResponse

```typescript
{
  id: string;              // セッションID
  status: string;          // "active" | "ended"
  started_at: string;      // ISO 8601
  ended_at: string | null; // ISO 8601 または null
}
```

### FeatureRequest

```typescript
{
  session_id: string | null;
  timestamp: string | null;
  facial: Record<string, number> | null;  // 0.0-1.0
  gaze: Record<string, number> | null;
  voice: Record<string, number> | null;
  extras: Record<string, any> | null;
}
```

### FeatureResponse

```typescript
{
  id: string;
  session_id: string | null;
  received_at: string;
  status: "accepted";
}
```

### AudioFormat (enum)

```typescript
"wav" | "opus" | "pcm"
```

### TranscriptionResult

```typescript
{
  text: string;        // 認識されたテキスト
  confidence: number;  // 0.0-1.0
  language: string;    // "ja", "en", etc.
  duration_ms: number; // 音声の長さ（ミリ秒）
}
```

### EmotionInterpretation

```typescript
{
  primary_emotion: string;               // "happy", "sad", "confused", etc.
  intensity: "low" | "medium" | "high";  // 強度
  description: string;                   // 自然言語での説明
  suggestion: string | null;             // 行動提案
}
```

### ResponseSuggestion

```typescript
{
  text: string;    // 応答文
  intent: string;  // "共感を示す", "質問する", "話題を深める", etc.
}
```

### AnalysisRequest

```typescript
{
  type: "ANALYSIS_REQUEST";
  session_id: string;
  timestamp: string;                      // ISO 8601
  emotion_scores: Record<string, number>;
  audio_data: string | null;              // Base64エンコード
  audio_format: string | null;
}
```

### AnalysisResponse

```typescript
{
  type: "ANALYSIS_RESPONSE";
  timestamp: string;                      // ISO 8601
  emotion: EmotionInterpretation;
  transcription: TranscriptionResult | null;
  suggestions: ResponseSuggestion[];      // 2パターン
  situation_analysis: string;
  processing_time_ms: number;
}
```

### LLMResponseResult (内部用)

```typescript
{
  responses: ResponseSuggestion[];  // 2パターン（異なる意図が必須）
  situation_analysis: string;
}
```

### CoachingResponse (内部用)

```typescript
{
  advice: string;             // 30文字以内のアドバイス
  strategy_tag: string;       // 戦略タグ (#共感 など)
  reply_candidates: string[]; // 返答候補リスト
  risk_score: number;         // 危険度 1-5
}
```

### Speaker (enum)

```typescript
"user" | "partner"
```

- `user`: XRデバイス装着者
- `partner`: 会話相手

### EmotionContext

```typescript
{
  primary_emotion: string;               // "happy", "confused", etc.
  emotion_scores: Record<string, number>;
}
```

### Utterance

```typescript
{
  speaker: Speaker;
  text: string;
  timestamp: string;                       // ISO 8601
  emotion_context: EmotionContext | null;
}
```

---

## 関連Issue

- [#30 サーバーサイドMVP実装](https://github.com/chofurenairengo/emotion-readar/issues/30)
  - #1 DTO定義
  - #2 STTサービス実装
  - #3 会話履歴管理サービス実装
  - #4 感情解釈サービス実装
  - #5 LLMサービス実装
  - #6 応答生成サービス実装
  - #7 WebSocketハンドラ拡張
