# サービス層仕様

> **Note**: このファイルは [SPECIFICATION.md](../../SPECIFICATION.md) の9章を分割したものです。

---

## 9.1 STTサービス（STTService）

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

## 9.2 会話履歴管理サービス（ConversationService）

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

## 9.3 感情解釈サービス（EmotionInterpreterService）

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

## 9.4 LLMサービス（LLMService）

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

## 9.5 応答生成サービス（ResponseGeneratorService）

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

## 9.6 セッションサービス（SessionService）

セッションのライフサイクルを管理する。

**メソッド**:

| メソッド                            | 説明               |
| ----------------------------------- | ------------------ |
| `create(user_id)`                   | 新規セッション作成 |
| `end(session_id)`                   | セッション終了     |
| `verify_owner(session_id, user_id)` | 所有者検証         |

## 9.7 レート制限（InMemoryRateLimiter）

Sliding Windowアルゴリズムによるインメモリレート制限。

**特徴**:
- パスパターン別の制限値設定
- UUID正規化による統一的なパス処理
- ウィンドウ期限切れ時の自動クリーンアップ
- ユーザー分離（ユーザーID単位での制限）

## 9.8 接続管理（ConnectionManager）

WebSocket接続のライフサイクル管理。

**機能**:
- セッション単位の接続追跡
- ブロードキャスト送信
- ターゲット指定送信
- 切断時のクリーンアップ

## 9.9 AIエージェント（ERAAgent / LoveCoachAgent）

Core層のプロンプトとGemini APIを繋ぐエージェント。

**インターフェース**:
```python
class AgentInterface(ABC):
    @abstractmethod
    async def run(self, input_data: dict) -> dict: ...
```
