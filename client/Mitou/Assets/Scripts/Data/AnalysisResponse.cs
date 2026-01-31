using System;
using Newtonsoft.Json;

namespace ERA.Data
{
    /// <summary>
    /// 解析結果レスポンス（WebSocket ANALYSIS_RESPONSE）を表すデータクラス。
    /// </summary>
    [Serializable]
    public class AnalysisResponse
    {
        /// <summary>
        /// メッセージタイプ ("ANALYSIS_RESPONSE")。
        /// </summary>
        [JsonProperty("type")]
        public string Type = string.Empty;

        /// <summary>
        /// サーバータイムスタンプ (ISO 8601)。
        /// </summary>
        [JsonProperty("timestamp")]
        public string Timestamp = string.Empty;

        /// <summary>
        /// 感情解釈。
        /// </summary>
        [JsonProperty("emotion")]
        public EmotionInterpretation Emotion = new EmotionInterpretation();

        /// <summary>
        /// STT結果（音声があった場合）。nullableなフィールド。
        /// </summary>
        [JsonProperty("transcription")]
        public TranscriptionResult Transcription;

        /// <summary>
        /// 応答候補（2パターン）。
        /// </summary>
        [JsonProperty("suggestions")]
        public ResponseSuggestion[] Suggestions = Array.Empty<ResponseSuggestion>();

        /// <summary>
        /// 状況分析。
        /// </summary>
        [JsonProperty("situation_analysis")]
        public string SituationAnalysis = string.Empty;

        /// <summary>
        /// 処理時間（ミリ秒）。
        /// </summary>
        [JsonProperty("processing_time_ms")]
        public int ProcessingTimeMs;
    }
}
