using System;
using Newtonsoft.Json;

namespace ERA.Data
{
    [Serializable]
    public class AnalysisResponse
    {
        [JsonProperty("type")]
        public string Type = string.Empty;

        [JsonProperty("timestamp")]
        public string Timestamp = string.Empty;

        [JsonProperty("emotion")]
        public EmotionInterpretation Emotion = new EmotionInterpretation();

        [JsonProperty("transcription")]
        public TranscriptionResult Transcription;

        [JsonProperty("suggestions")]
        public ResponseSuggestion[] Suggestions = Array.Empty<ResponseSuggestion>();

        [JsonProperty("situation_analysis")]
        public string SituationAnalysis = string.Empty;

        [JsonProperty("processing_time_ms")]
        public int ProcessingTimeMs;
    }
}
