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
}
