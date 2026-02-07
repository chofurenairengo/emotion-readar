using System;
using Newtonsoft.Json;

namespace ERA.Network
{
    /// <summary>
    /// 受信メッセージのタイプ判定用基底クラス。
    /// </summary>
    [Serializable]
    public class BaseMessage
    {
        [JsonProperty("type")]
        public string Type = string.Empty;

        [JsonProperty("timestamp")]
        public string Timestamp = string.Empty;
    }

    /// <summary>
    /// PONGメッセージ（サーバー → クライアント）。
    /// </summary>
    [Serializable]
    public class PongMessage : BaseMessage { }

    /// <summary>
    /// RESET_ACKメッセージ（サーバー → クライアント）。
    /// </summary>
    [Serializable]
    public class ResetAckMessage : BaseMessage { }

    /// <summary>
    /// ERROR_ACKメッセージ（サーバー → クライアント）。
    /// </summary>
    [Serializable]
    public class ErrorAckMessage : BaseMessage { }

    /// <summary>
    /// ERRORメッセージ（サーバー → クライアント）。
    /// </summary>
    [Serializable]
    public class ErrorMessage : BaseMessage
    {
        [JsonProperty("message")]
        public string Message = string.Empty;

        [JsonProperty("detail")]
        public string Detail = string.Empty;
    }

    /// <summary>
    /// PINGメッセージ（クライアント → サーバー）。
    /// </summary>
    [Serializable]
    public class PingMessage
    {
        [JsonProperty("type")]
        public string Type = "PING";
    }

    /// <summary>
    /// RESETメッセージ（クライアント → サーバー）。
    /// </summary>
    [Serializable]
    public class ResetMessage
    {
        [JsonProperty("type")]
        public string Type = "RESET";
    }

    /// <summary>
    /// ANALYSIS_REQUESTメッセージ（クライアント → サーバー）。
    /// </summary>
    [Serializable]
    public class AnalysisRequestMessage
    {
        [JsonProperty("type")]
        public string Type = "ANALYSIS_REQUEST";

        [JsonProperty("session_id")]
        public string SessionId = string.Empty;

        [JsonProperty("emotion_scores")]
        public System.Collections.Generic.Dictionary<string, float> EmotionScores =
            new System.Collections.Generic.Dictionary<string, float>();

        [JsonProperty("audio_data")]
        public string AudioData;

        [JsonProperty("audio_format")]
        public string AudioFormat;
    }
}
