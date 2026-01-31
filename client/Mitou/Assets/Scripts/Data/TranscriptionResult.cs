using System;
using Newtonsoft.Json;

namespace ERA.Data
{
    /// <summary>
    /// STT（音声認識）結果を表すデータクラス。
    /// </summary>
    [Serializable]
    public class TranscriptionResult
    {
        /// <summary>
        /// 認識されたテキスト。
        /// </summary>
        [JsonProperty("text")]
        public string Text;

        /// <summary>
        /// 信頼度 (0.0〜1.0)。
        /// </summary>
        [JsonProperty("confidence")]
        public float Confidence;

        /// <summary>
        /// 検出言語 ("ja", "en")。
        /// </summary>
        [JsonProperty("language")]
        public string Language;

        /// <summary>
        /// 音声の長さ（ミリ秒）。
        /// </summary>
        [JsonProperty("duration_ms")]
        public int DurationMs;
    }
}
