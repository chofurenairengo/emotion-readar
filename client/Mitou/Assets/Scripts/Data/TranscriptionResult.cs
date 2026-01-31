using System;
using Newtonsoft.Json;

namespace ERA.Data
{
    [Serializable]
    public class TranscriptionResult
    {
        [JsonProperty("text")]
        public string Text = string.Empty;

        [JsonProperty("confidence")]
        public float Confidence;

        [JsonProperty("language")]
        public string Language = string.Empty;

        [JsonProperty("duration_ms")]
        public int DurationMs;
    }
}
