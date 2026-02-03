using UnityEngine;
using ERA.Network;

namespace ERA.Test
{
    /// <summary>
    /// WebSocket接続のテスト用スクリプト。
    /// GameObjectにアタッチしてPlay時に自動接続する。
    /// </summary>
    public class WebSocketConnectionTest : MonoBehaviour
    {
        [SerializeField] private WebSocketClient _webSocketClient;
        [SerializeField] private string _testSessionId = "test-session-001";

        private async void Start()
        {
            if (_webSocketClient == null)
            {
                Debug.LogError("[WebSocketConnectionTest] WebSocketClient is not assigned!");
                return;
            }

            Debug.Log($"[WebSocketConnectionTest] Connecting with session: {_testSessionId}");

            // 接続状態変更のイベント購読
            _webSocketClient.OnStateChanged += OnStateChanged;
            _webSocketClient.OnAnalysisResponse += OnAnalysisResponse;
            _webSocketClient.OnError += OnError;

            // 接続開始
            await _webSocketClient.Connect(_testSessionId);
        }

        private void OnDestroy()
        {
            if (_webSocketClient != null)
            {
                _webSocketClient.OnStateChanged -= OnStateChanged;
                _webSocketClient.OnAnalysisResponse -= OnAnalysisResponse;
                _webSocketClient.OnError -= OnError;
            }
        }

        private void OnStateChanged(ConnectionState state)
        {
            Debug.Log($"[WebSocketConnectionTest] State changed: {state}");
        }

        private void OnAnalysisResponse(Data.AnalysisResponse response)
        {
            Debug.Log($"[WebSocketConnectionTest] Received AnalysisResponse: " +
                $"emotion={response?.Emotion?.PrimaryEmotion}, " +
                $"suggestions={response?.Suggestions?.Length ?? 0}");
        }

        private void OnError(ErrorMessage error)
        {
            Debug.LogWarning($"[WebSocketConnectionTest] Error: {error?.Message}");
        }
    }
}
