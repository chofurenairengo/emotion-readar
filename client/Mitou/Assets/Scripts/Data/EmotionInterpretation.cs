using System;
using Newtonsoft.Json;

namespace ERA.Data
{
    /// <summary>
    /// 感情スコアの解釈結果を表すデータクラス。
    /// </summary>
    [Serializable]
    public class EmotionInterpretation
    {
        /// <summary>
        /// 主要な感情 ("happy", "sad", "confused" 等)。
        /// </summary>
        [JsonProperty("primary_emotion")]
        public string PrimaryEmotion;

        /// <summary>
        /// 強度 ("low", "medium", "high")。
        /// </summary>
        [JsonProperty("intensity")]
        public string Intensity;

        /// <summary>
        /// 自然言語での説明（例: "相手は楽しそうです"）。
        /// </summary>
        [JsonProperty("description")]
        public string Description;

        /// <summary>
        /// 行動提案（例: "話題を広げましょう"）。nullableなフィールド。
        /// </summary>
        [JsonProperty("suggestion")]
        public string Suggestion;
    }
}
