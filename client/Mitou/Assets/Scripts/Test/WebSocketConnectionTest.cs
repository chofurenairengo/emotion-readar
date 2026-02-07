using UnityEngine;
using ERA.Network;
using System;
#if UNITY_ANDROID
using UnityEngine.Android;
#endif

namespace ERA.Test
{
    /// <summary>
    /// WebSocket接続のテスト用スクリプト。
    /// GameObjectにアタッチしてPlay時に自動接続する。
    /// ANALYSIS_RESPONSEの受信ログも出力する。
    /// </summary>
    public class WebSocketConnectionTest : MonoBehaviour
    {
        [SerializeField] private WebSocketClient _webSocketClient;
        [SerializeField] private string _testSessionId = "test-session-001";
        [Header("Native Bridge (Android)")]
        [SerializeField] private bool _useNativeBridge = true;
        [SerializeField] private bool _useExternalCameraOnly = true;
        [SerializeField] private bool _autoRequestCameraPermission = true;
        [SerializeField] private string _nativeWsHost = "";
        [SerializeField] private string _bridgeClassName = "com.commuxr.unityplugin.bridge.EraBridge";

        [Serializable]
        private class BridgeMessage
        {
            public string type;
            public string message;
        }

        private async void Start()
        {
            if (_webSocketClient == null)
            {
                Debug.LogError("[WebSocketConnectionTest] WebSocketClient is not assigned!");
                return;
            }

            Debug.Log($"[WebSocketConnectionTest] Connecting with session: {_testSessionId}");

            _webSocketClient.OnStateChanged += OnStateChanged;
            _webSocketClient.OnAnalysisResponse += OnAnalysisResponse;
            _webSocketClient.OnError += OnError;

            StartNativeBridge();
            await _webSocketClient.Connect(_testSessionId);
        }

        private void OnDestroy()
        {
            StopNativeBridge();

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

            if (response?.Suggestions != null)
            {
                for (int i = 0; i < response.Suggestions.Length; i++)
                {
                    Debug.Log($"  Suggestion[{i}]: {response.Suggestions[i]?.Text}");
                }
            }
        }

        private void OnError(ErrorMessage error)
        {
            Debug.LogWarning($"[WebSocketConnectionTest] Error: {error?.Message}");
        }

        // EraBridge から UnityMessageSender 経由で呼ばれる。
        public void OnBridgeMessage(string json)
        {
            if (string.IsNullOrEmpty(json))
            {
                return;
            }

            try
            {
                var msg = JsonUtility.FromJson<BridgeMessage>(json);
                Debug.Log($"[WebSocketConnectionTest][Bridge] {msg?.type}: {msg?.message}");
            }
            catch (Exception ex)
            {
                Debug.LogWarning($"[WebSocketConnectionTest] Bridge message parse failed: {ex.Message}, raw={json}");
            }
        }

        private void StartNativeBridge()
        {
            if (!_useNativeBridge)
            {
                return;
            }

#if UNITY_ANDROID && !UNITY_EDITOR
            if (_useExternalCameraOnly)
            {
                StartNativeBridgeCore();
                return;
            }

            if (!Permission.HasUserAuthorizedPermission(Permission.Camera))
            {
                if (_autoRequestCameraPermission)
                {
                    var callbacks = new PermissionCallbacks();
                    callbacks.PermissionGranted += _ =>
                    {
                        Debug.Log("[WebSocketConnectionTest] CAMERA permission granted");
                        StartNativeBridgeCore();
                    };
                    callbacks.PermissionDenied += _ =>
                    {
                        Debug.LogError("[WebSocketConnectionTest] CAMERA permission denied");
                    };
                    callbacks.PermissionDeniedAndDontAskAgain += _ =>
                    {
                        Debug.LogError("[WebSocketConnectionTest] CAMERA permission denied (don't ask again)");
                    };
                    Permission.RequestUserPermission(Permission.Camera, callbacks);
                }
                else
                {
                    Debug.LogWarning("[WebSocketConnectionTest] CAMERA permission missing. Enable auto request or grant manually.");
                }
                return;
            }

            StartNativeBridgeCore();
#else
            Debug.Log("[WebSocketConnectionTest] Native bridge is Android device only");
#endif
        }

        private void StartNativeBridgeCore()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                string host = string.IsNullOrWhiteSpace(_nativeWsHost)
                    ? _webSocketClient.ServerHost
                    : _nativeWsHost;

                using (var bridge = new AndroidJavaClass(_bridgeClassName))
                {
                    bridge.CallStatic("setUnityReceiver", gameObject.name, nameof(OnBridgeMessage));
                    var cameraMode = _useExternalCameraOnly ? "external" : "internal";
                    bridge.CallStatic("start", _testSessionId, host, cameraMode);
                }
                Debug.Log($"[WebSocketConnectionTest] EraBridge started: session={_testSessionId}, host={host}, externalOnly={_useExternalCameraOnly}");
            }
            catch (Exception ex)
            {
                Debug.LogError($"[WebSocketConnectionTest] EraBridge start failed: {ex.Message}");
            }
#endif
        }

        private void StopNativeBridge()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (!_useNativeBridge)
            {
                return;
            }

            try
            {
                using (var bridge = new AndroidJavaClass(_bridgeClassName))
                {
                    bridge.CallStatic("stop");
                }
                Debug.Log("[WebSocketConnectionTest] EraBridge stopped");
            }
            catch (Exception ex)
            {
                Debug.LogWarning($"[WebSocketConnectionTest] EraBridge stop failed: {ex.Message}");
            }
#endif
        }

        /// <summary>
        /// 外部カメラ側で推定した感情スコアJSONをAndroidブリッジへ渡す。
        /// 例: {"happy":0.7,"sad":0.1,"angry":0.0,"confused":0.2,"surprised":0.0,"fearful":0.0,"disgusted":0.0}
        /// </summary>
        public void SubmitExternalEmotionScores(string emotionScoresJson)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (!_useNativeBridge)
            {
                Debug.LogWarning("[WebSocketConnectionTest] Native bridge is disabled");
                return;
            }

            if (!_useExternalCameraOnly)
            {
                Debug.LogWarning("[WebSocketConnectionTest] External score submit is intended for external-only mode");
            }

            try
            {
                using (var bridge = new AndroidJavaClass(_bridgeClassName))
                {
                    bridge.CallStatic("submitExternalEmotionScores", emotionScoresJson);
                }
            }
            catch (Exception ex)
            {
                Debug.LogError($"[WebSocketConnectionTest] submitExternalEmotionScores failed: {ex.Message}");
            }
#endif
        }
    }
}
