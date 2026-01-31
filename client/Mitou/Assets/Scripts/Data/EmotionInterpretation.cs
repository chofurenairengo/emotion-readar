using System;
using Newtonsoft.Json;

namespace ERA.Data
{
    [Serializable]
    public class EmotionInterpretation
    {
        [JsonProperty("primary_emotion")]
        public string PrimaryEmotion = string.Empty;

        [JsonProperty("intensity")]
        public string Intensity = string.Empty;

        [JsonProperty("description")]
        public string Description = string.Empty;

        [JsonProperty("suggestion")]
        public string Suggestion;
    }
}
