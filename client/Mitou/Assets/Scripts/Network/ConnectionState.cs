namespace ERA.Network
{
    /// <summary>
    /// WebSocket接続状態を表す列挙型。
    /// </summary>
    public enum ConnectionState
    {
        /// <summary>切断状態（初期状態）。</summary>
        Disconnected,

        /// <summary>接続中。</summary>
        Connecting,

        /// <summary>接続完了。</summary>
        Connected,

        /// <summary>再接続中。</summary>
        Reconnecting,

        /// <summary>エラー状態（再接続上限超過など）。</summary>
        Error
    }
}
