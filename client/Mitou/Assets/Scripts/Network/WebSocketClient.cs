using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using UnityEngine;
using NativeWebSocket;
using Newtonsoft.Json;
using ERA.Data;

namespace ERA.Network
{
    /// <summary>
    /// サーバーとのWebSocket通信を管理するクライアントクラス。
    /// </summary>
    public class WebSocketClient : MonoBehaviour
    {
        #region Inspector Settings

        [Header("接続設定")]
        [SerializeField] private string _serverHost = "ws://localhost:8000";

        [Header("ハートビート設定")]
        [SerializeField] private float _pingIntervalSeconds = 30f;
        [SerializeField] private float _pongTimeoutSeconds = 10f;

        [Header("再接続設定")]
        [SerializeField] private int _maxRetryCount = 5;
        [SerializeField] private float _initialRetryDelaySeconds = 1f;
        [SerializeField] private float _maxRetryDelaySeconds = 16f;

        [Header("デバッグ")]
        [SerializeField] private bool _logMessages = false;

        #endregion

        #region Public Properties

        /// <summary>現在の接続状態。</summary>
        public ConnectionState State => _state;

        /// <summary>現在のセッションID。</summary>
        public string SessionId { get; private set; } = string.Empty;

        /// <summary>認証トークン。</summary>
        public string Token { get; private set; } = string.Empty;

        /// <summary>接続先WebSocketホスト。</summary>
        public string ServerHost => _serverHost;

        #endregion

        #region Events

        /// <summary>接続状態変更イベント。</summary>
        public event Action<ConnectionState> OnStateChanged;

        /// <summary>ANALYSIS_RESPONSE受信イベント。</summary>
        public event Action<AnalysisResponse> OnAnalysisResponse;

        /// <summary>ERRORメッセージ受信イベント。</summary>
        public event Action<ErrorMessage> OnError;

        /// <summary>RESET_ACK受信イベント。</summary>
        public event Action OnResetAck;

        #endregion

        #region Private Fields

        private WebSocket _ws;
        private readonly Queue<string> _mainThreadQueue = new Queue<string>();
        private volatile ConnectionState _state = ConnectionState.Disconnected;

        // ハートビート管理
        private float _lastPingSentTime;
        private bool _waitingForPong;

        // 再接続管理
        private int _currentRetryCount;
        private float _currentRetryDelay;
        private bool _isReconnecting;

        #endregion

        #region Unity Lifecycle

        private void Update()
        {
            // NativeWebSocket メッセージディスパッチ
#if !UNITY_WEBGL || UNITY_EDITOR
            _ws?.DispatchMessageQueue();
#endif
            // メインスレッドキュー処理
            ProcessMainThreadQueue();

            // ハートビート処理
            if (_state == ConnectionState.Connected)
            {
                ProcessHeartbeat();
            }
        }

        private async void OnApplicationQuit()
        {
            await Disconnect();
        }

        private async void OnDestroy()
        {
            await Disconnect();
        }

        #endregion

        #region Public Methods

        /// <summary>
        /// WebSocket接続を開始する。
        /// </summary>
        /// <param name="sessionId">セッションID。</param>
        /// <param name="token">Firebase ID Token（DEV_AUTH_BYPASS=false環境で必須）。</param>
        public async Task Connect(string sessionId, string token = "")
        {
            if (_state == ConnectionState.Connected || _state == ConnectionState.Connecting)
            {
                Debug.LogWarning("[WebSocketClient] Already connected or connecting");
                return;
            }

            SessionId = sessionId;
            Token = token;
            _currentRetryCount = 0;
            _currentRetryDelay = _initialRetryDelaySeconds;

            await ConnectInternal();
        }

        /// <summary>
        /// WebSocket接続を切断する。
        /// </summary>
        public async Task Disconnect()
        {
            _isReconnecting = false;
            UnsubscribeEvents();

            if (_ws == null) return;

            try
            {
                SetState(ConnectionState.Disconnected);
                await _ws.Close();
            }
            catch (Exception ex)
            {
                Debug.LogError($"[WebSocketClient] Disconnect error: {ex.Message}");
            }
            finally
            {
                _ws = null;
            }
        }

        /// <summary>
        /// RESETメッセージを送信する。
        /// </summary>
        public async Task SendReset()
        {
            if (_state != ConnectionState.Connected)
            {
                Debug.LogWarning("[WebSocketClient] Not connected, cannot send RESET");
                return;
            }

            var message = new ResetMessage();
            await SendMessage(message);
        }

        /// <summary>
        /// ANALYSIS_REQUESTメッセージを送信する。
        /// </summary>
        /// <param name="emotionScores">感情スコア（例: {"happy": 0.8, "neutral": 0.2}）</param>
        /// <param name="audioData">Base64エンコード済み音声データ（オプション）</param>
        /// <param name="audioFormat">音声フォーマット（オプション、例: "wav"）</param>
        public async Task SendAnalysisRequest(
            Dictionary<string, float> emotionScores,
            string audioData = null,
            string audioFormat = null)
        {
            if (_state != ConnectionState.Connected)
            {
                Debug.LogWarning("[WebSocketClient] Not connected, cannot send ANALYSIS_REQUEST");
                return;
            }

            var message = new AnalysisRequestMessage
            {
                SessionId = SessionId,
                EmotionScores = emotionScores,
                AudioData = audioData,
                AudioFormat = audioFormat,
            };
            await SendMessage(message);
        }

        #endregion

        #region Private Methods - Connection

        private async Task ConnectInternal()
        {
            UnsubscribeEvents();

            if (_isReconnecting)
            {
                SetState(ConnectionState.Reconnecting);
            }
            else
            {
                SetState(ConnectionState.Connecting);
            }

            string url = $"{_serverHost}/api/realtime?session_id={SessionId}&token={Token}";
            _ws = new WebSocket(url);

            _ws.OnOpen += HandleOpen;
            _ws.OnClose += HandleClose;
            _ws.OnError += HandleError;
            _ws.OnMessage += HandleMessage;

            try
            {
                await _ws.Connect();
            }
            catch (Exception ex)
            {
                Debug.LogError($"[WebSocketClient] Connect failed: {ex.Message}");
                await HandleConnectionFailure();
            }
        }

        private void UnsubscribeEvents()
        {
            if (_ws == null) return;

            _ws.OnOpen -= HandleOpen;
            _ws.OnClose -= HandleClose;
            _ws.OnError -= HandleError;
            _ws.OnMessage -= HandleMessage;
        }

        private void HandleOpen()
        {
            Debug.Log($"[WebSocketClient] Connected to {_serverHost}");

            SetState(ConnectionState.Connected);
            _currentRetryCount = 0;
            _currentRetryDelay = _initialRetryDelaySeconds;
            _isReconnecting = false;

            // ハートビート初期化
            _lastPingSentTime = Time.time;
            _waitingForPong = false;
        }

        private async void HandleClose(WebSocketCloseCode closeCode)
        {
            Debug.Log($"[WebSocketClient] Connection closed: {closeCode}");

            if (_state != ConnectionState.Disconnected)
            {
                try
                {
                    await HandleConnectionFailure();
                }
                catch (Exception ex)
                {
                    Debug.LogError($"[WebSocketClient] HandleClose error: {ex.Message}");
                    SetState(ConnectionState.Error);
                }
            }
        }

        private async void HandleError(string error)
        {
            Debug.LogError($"[WebSocketClient] Error: {error}");

            if (_state == ConnectionState.Connected || _state == ConnectionState.Connecting)
            {
                try
                {
                    await HandleConnectionFailure();
                }
                catch (Exception ex)
                {
                    Debug.LogError($"[WebSocketClient] HandleError error: {ex.Message}");
                    SetState(ConnectionState.Error);
                }
            }
        }

        private async Task HandleConnectionFailure()
        {
            if (_currentRetryCount >= _maxRetryCount)
            {
                Debug.LogError($"[WebSocketClient] Max retry count ({_maxRetryCount}) exceeded");
                SetState(ConnectionState.Error);
                return;
            }

            _isReconnecting = true;
            _currentRetryCount++;

            Debug.Log($"[WebSocketClient] Reconnecting in {_currentRetryDelay}s (attempt {_currentRetryCount}/{_maxRetryCount})");

            await Task.Delay((int)(_currentRetryDelay * 1000));

            // Exponential backoff
            _currentRetryDelay = Mathf.Min(_currentRetryDelay * 2, _maxRetryDelaySeconds);

            await ConnectInternal();
        }

        #endregion

        #region Private Methods - Message Handling

        private void HandleMessage(byte[] data)
        {
            string json = System.Text.Encoding.UTF8.GetString(data);

            lock (_mainThreadQueue)
            {
                _mainThreadQueue.Enqueue(json);
            }
        }

        private void ProcessMainThreadQueue()
        {
            while (true)
            {
                string json = null;

                lock (_mainThreadQueue)
                {
                    if (_mainThreadQueue.Count > 0)
                    {
                        json = _mainThreadQueue.Dequeue();
                    }
                }

                if (json == null) break;

                ProcessMessage(json);
            }
        }

        private void ProcessMessage(string json)
        {
            if (_logMessages)
            {
                Debug.Log($"[WebSocketClient] Received: {json}");
            }

            try
            {
                var baseMsg = JsonConvert.DeserializeObject<BaseMessage>(json);

                if (baseMsg == null || string.IsNullOrEmpty(baseMsg.Type))
                {
                    Debug.LogWarning($"[WebSocketClient] Unknown message format: {json}");
                    return;
                }

                switch (baseMsg.Type)
                {
                    case "PONG":
                        HandlePong();
                        break;

                    case "ANALYSIS_RESPONSE":
                        var analysisResponse = JsonConvert.DeserializeObject<AnalysisResponse>(json);
                        if (analysisResponse != null)
                        {
                            OnAnalysisResponse?.Invoke(analysisResponse);
                        }
                        else
                        {
                            Debug.LogWarning("[WebSocketClient] Failed to deserialize ANALYSIS_RESPONSE");
                        }
                        break;

                    case "ERROR":
                        var errorMsg = JsonConvert.DeserializeObject<ErrorMessage>(json);
                        if (errorMsg != null)
                        {
                            Debug.LogWarning($"[WebSocketClient] Server error: {errorMsg.Message}");
                            OnError?.Invoke(errorMsg);
                        }
                        break;

                    case "RESET_ACK":
                        Debug.Log("[WebSocketClient] Reset acknowledged");
                        OnResetAck?.Invoke();
                        break;

                    case "ERROR_ACK":
                        Debug.Log("[WebSocketClient] Error report acknowledged");
                        break;

                    default:
                        Debug.LogWarning($"[WebSocketClient] Unhandled message type: {baseMsg.Type}");
                        break;
                }
            }
            catch (Exception ex)
            {
                Debug.LogError($"[WebSocketClient] Message parse error: {ex.Message}\n{json}");
            }
        }

        #endregion

        #region Private Methods - Heartbeat

        private void ProcessHeartbeat()
        {
            float currentTime = Time.time;

            // PONG待機中のタイムアウトチェック
            if (_waitingForPong)
            {
                if (currentTime - _lastPingSentTime > _pongTimeoutSeconds)
                {
                    Debug.LogWarning("[WebSocketClient] PONG timeout, triggering reconnect");
                    _waitingForPong = false;
                    FireAndForgetAsync(HandleConnectionFailure(), "Heartbeat timeout reconnect");
                    return;
                }
            }

            // PING送信間隔チェック
            if (currentTime - _lastPingSentTime >= _pingIntervalSeconds)
            {
                FireAndForgetAsync(SendPing(), "SendPing");
            }
        }

        private async Task SendPing()
        {
            if (_state != ConnectionState.Connected) return;

            var pingMessage = new PingMessage();
            await SendMessage(pingMessage);

            _lastPingSentTime = Time.time;
            _waitingForPong = true;

            if (_logMessages)
            {
                Debug.Log("[WebSocketClient] PING sent");
            }
        }

        private void HandlePong()
        {
            _waitingForPong = false;

            if (_logMessages)
            {
                Debug.Log("[WebSocketClient] PONG received");
            }
        }

        #endregion

        #region Private Methods - Utility

        private async Task SendMessage<T>(T message)
        {
            if (_ws == null || _ws.State != WebSocketState.Open)
            {
                Debug.LogWarning("[WebSocketClient] Cannot send message, not connected");
                return;
            }

            string json = JsonConvert.SerializeObject(message);
            await _ws.SendText(json);

            if (_logMessages)
            {
                Debug.Log($"[WebSocketClient] Sent: {json}");
            }
        }

        private void SetState(ConnectionState newState)
        {
            if (_state == newState) return;

            ConnectionState oldState = _state;
            _state = newState;

            Debug.Log($"[WebSocketClient] State: {oldState} -> {newState}");
            OnStateChanged?.Invoke(newState);
        }

        private async void FireAndForgetAsync(Task task, string context)
        {
            try
            {
                await task;
            }
            catch (Exception ex)
            {
                Debug.LogError($"[WebSocketClient] {context} error: {ex.Message}");
            }
        }

        #endregion
    }
}
