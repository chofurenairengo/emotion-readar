using System;
using Newtonsoft.Json;

namespace ERA.Data
{
    [Serializable]
    public class ResponseSuggestion
    {
        [JsonProperty("text")]
        public string Text = string.Empty;

        [JsonProperty("intent")]
        public string Intent = string.Empty;
    }
}
