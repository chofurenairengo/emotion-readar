# データモデル・DTO定義

> **Note**: このファイルは [SPECIFICATION.md](../../SPECIFICATION.md) の8章を分割したものです。

---

## 8.1 DTO一覧

### 8.1.1 AudioFormat (enum)

```python
class AudioFormat(str, Enum):
    WAV = "wav"
    OPUS = "opus"
    PCM = "pcm"
```

### 8.1.2 TranscriptionResult

| フィールド    | 型    | 説明                     |
| ------------- | ----- | ------------------------ |
| `text`        | str   | 認識されたテキスト       |
| `confidence`  | float | 信頼度 (0.0-1.0)         |
| `language`    | str   | 検出言語 ("ja", "en" 等) |
| `duration_ms` | int   | 音声の長さ（ミリ秒）     |

### 8.1.3 Speaker (enum)

```python
class Speaker(str, Enum):
    USER = "user"       # XRデバイス装着者
    PARTNER = "partner"  # 会話相手
```

### 8.1.4 EmotionContext

| フィールド        | 型               | 説明         |
| ----------------- | ---------------- | ------------ |
| `primary_emotion` | str              | 主要な感情   |
| `emotion_scores`  | dict[str, float] | 全感情スコア |

### 8.1.5 Utterance

| フィールド        | 型                     | 説明                  |
| ----------------- | ---------------------- | --------------------- |
| `speaker`         | Speaker                | 話者 (USER / PARTNER) |
| `text`            | str                    | 発話内容              |
| `timestamp`       | datetime               | タイムスタンプ        |
| `emotion_context` | EmotionContext \| None | 感情コンテキスト      |

### 8.1.6 EmotionInterpretation

| フィールド        | 型          | 説明                             |
| ----------------- | ----------- | -------------------------------- |
| `primary_emotion` | str         | 主要な感情                       |
| `intensity`       | str         | 強度 ("low" / "medium" / "high") |
| `description`     | str         | 自然言語での説明                 |
| `suggestion`      | str \| None | 行動提案                         |

### 8.1.7 EmotionChange

| フィールド     | 型  | 説明         |
| -------------- | --- | ------------ |
| `from_emotion` | str | 変化前の感情 |
| `to_emotion`   | str | 変化後の感情 |
| `description`  | str | 変化の説明   |

### 8.1.8 ResponseSuggestion

| フィールド | 型  | 説明                                |
| ---------- | --- | ----------------------------------- |
| `text`     | str | 応答文                              |
| `intent`   | str | 意図（"共感を示す", "質問する" 等） |

### 8.1.9 LLMResponseResult

| フィールド           | 型                       | 説明                                    |
| -------------------- | ------------------------ | --------------------------------------- |
| `responses`          | list[ResponseSuggestion] | 応答候補（2パターン、異なる意図が必須） |
| `situation_analysis` | str                      | 状況分析                                |

### 8.1.10 AnalysisRequest

| フィールド       | 型               | デフォルト         | 説明                       |
| ---------------- | ---------------- | ------------------ | -------------------------- |
| `type`           | str              | "ANALYSIS_REQUEST" | メッセージタイプ           |
| `session_id`     | str              | （必須）           | セッションID               |
| `timestamp`      | datetime         | （必須）           | クライアントタイムスタンプ |
| `emotion_scores` | dict[str, float] | （必須）           | 感情スコア                 |
| `audio_data`     | str \| None      | None               | 音声データ（Base64）       |
| `audio_format`   | str \| None      | None               | 音声フォーマット           |

### 8.1.11 AnalysisResponse

| フィールド           | 型                          | デフォルト          | 説明                   |
| -------------------- | --------------------------- | ------------------- | ---------------------- |
| `type`               | str                         | "ANALYSIS_RESPONSE" | メッセージタイプ       |
| `timestamp`          | datetime                    | （必須）            | サーバータイムスタンプ |
| `emotion`            | EmotionInterpretation       | （必須）            | 感情解釈               |
| `transcription`      | TranscriptionResult \| None | None                | STT結果                |
| `suggestions`        | list[ResponseSuggestion]    | （必須）            | 応答候補               |
| `situation_analysis` | str                         | （必須）            | 状況分析               |
| `processing_time_ms` | int                         | （必須）            | 処理時間               |

### 8.1.12 CoachingResponse（内部用）

| フィールド         | 型        | 説明                   |
| ------------------ | --------- | ---------------------- |
| `advice`           | str       | 30文字以内のアドバイス |
| `strategy_tag`     | str       | 戦略タグ (#共感 など)  |
| `reply_candidates` | list[str] | 返答候補リスト         |
| `risk_score`       | int       | 危険度 (1-5)           |

## 8.2 ドメインモデル

### 8.2.1 Session

| フィールド   | 型               | 説明                |
| ------------ | ---------------- | ------------------- |
| `id`         | str              | セッションID (UUID) |
| `user_id`    | str              | 所有者のユーザーID  |
| `status`     | str              | "active" / "ended"  |
| `started_at` | datetime         | 開始時刻 (UTC)      |
| `ended_at`   | datetime \| None | 終了時刻            |

### 8.2.2 FeatureLog

| フィールド    | 型           | 説明          |
| ------------- | ------------ | ------------- |
| `id`          | str          | ログID (UUID) |
| `session_id`  | str          | セッションID  |
| `received_at` | datetime     | 受信時刻      |
| `facial`      | dict \| None | 顔特徴量      |
| `gaze`        | dict \| None | 視線特徴量    |
| `voice`       | dict \| None | 音声特徴量    |
