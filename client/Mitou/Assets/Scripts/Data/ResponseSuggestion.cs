using System;
using Newtonsoft.Json;

namespace ERA.Data
{
    /// <summary>
    /// 応答候補を表すデータクラス。
    /// </summary>
    [Serializable]
    public class ResponseSuggestion
    {
        /// <summary>
        /// 応答文（例: "それは面白いですね"）。
        /// </summary>
        [JsonProperty("text")]
        public string Text;

        /// <summary>
        /// 意図（例: "共感を示す", "質問する" 等）。
        /// </summary>
        [JsonProperty("intent")]
        public string Intent;
    }
}
