using UnityEngine;
using ERA.Camera;
using ERA.Data;
using ERA.Network;

namespace ERA.UI
{
    /// <summary>
    /// メイン画面の統括コンポーネント。
    /// WebSocketClientからの応答を受信し、EmotionOverlayとSuggestionOverlayを更新する。
    /// カメラプレビューの管理も行う。Android/XRデバイスで共通のUIを提供する。
    /// </summary>
    public class MainScreen : MonoBehaviour
    {
        [Header("UI Components")]
        [SerializeField] private EmotionOverlay _emotionOverlay;
        [SerializeField] private SuggestionOverlay _suggestionOverlay;

        [Header("Camera")]
        [SerializeField] private CameraPreviewType _cameraPreviewType = CameraPreviewType.Auto;
        [SerializeField] private GameObject _cameraPreviewTarget;

        [Header("Network")]
        [SerializeField] private WebSocketClient _webSocketClient;

        [Header("Debug")]
        [SerializeField] private bool _logResponses = false;

        private ICameraPreview _cameraPreview;

        private void Awake()
        {
            InitializeCamera();
        }

        private void Start()
        {
            ValidateComponents();
            SubscribeToEvents();
            InitializeUI();
            StartCamera();
        }

        private void OnDestroy()
        {
            UnsubscribeFromEvents();
            DisposeCamera();
        }

        /// <summary>
        /// WebSocketClientを設定する（外部から注入する場合）。
        /// </summary>
        /// <param name="client">WebSocketClientインスタンス</param>
        public void SetWebSocketClient(WebSocketClient client)
        {
            UnsubscribeFromEvents();
            _webSocketClient = client;
            SubscribeToEvents();
        }

        /// <summary>
        /// UIを初期状態にリセットする。
        /// </summary>
        public void ResetUI()
        {
            if (_emotionOverlay != null)
            {
                _emotionOverlay.Clear();
            }

            if (_suggestionOverlay != null)
            {
                _suggestionOverlay.Clear();
            }
        }

        private void ValidateComponents()
        {
            if (_emotionOverlay == null)
            {
                Debug.LogWarning("[MainScreen] EmotionOverlay is not assigned");
            }

            if (_suggestionOverlay == null)
            {
                Debug.LogWarning("[MainScreen] SuggestionOverlay is not assigned");
            }

            if (_webSocketClient == null)
            {
                Debug.LogWarning("[MainScreen] WebSocketClient is not assigned. " +
                    "Use SetWebSocketClient() or assign in Inspector.");
            }
        }

        private void SubscribeToEvents()
        {
            if (_webSocketClient != null)
            {
                _webSocketClient.OnAnalysisResponse += HandleAnalysisResponse;
                _webSocketClient.OnResetAck += HandleResetAck;
            }
        }

        private void UnsubscribeFromEvents()
        {
            if (_webSocketClient != null)
            {
                _webSocketClient.OnAnalysisResponse -= HandleAnalysisResponse;
                _webSocketClient.OnResetAck -= HandleResetAck;
            }
        }

        private void InitializeUI()
        {
            if (_emotionOverlay != null)
            {
                _emotionOverlay.Clear();
            }

            if (_suggestionOverlay != null)
            {
                _suggestionOverlay.Clear();
            }
        }

        private void HandleAnalysisResponse(AnalysisResponse response)
        {
            if (response == null)
            {
                Debug.LogWarning("[MainScreen] Received null AnalysisResponse");
                return;
            }

            if (_logResponses)
            {
                Debug.Log($"[MainScreen] Received AnalysisResponse: " +
                    $"emotion={response.Emotion?.PrimaryEmotion}, " +
                    $"suggestions={response.Suggestions?.Length ?? 0}");
            }

            UpdateEmotionDisplay(response.Emotion);
            UpdateSuggestionDisplay(response.Suggestions);
        }

        private void HandleResetAck()
        {
            if (_logResponses)
            {
                Debug.Log("[MainScreen] Received RESET_ACK, clearing UI");
            }

            ResetUI();
        }

        private void UpdateEmotionDisplay(EmotionInterpretation emotion)
        {
            if (_emotionOverlay == null)
            {
                return;
            }

            if (emotion == null || string.IsNullOrEmpty(emotion.PrimaryEmotion))
            {
                _emotionOverlay.Clear();
                return;
            }

            _emotionOverlay.UpdateEmotion(emotion.PrimaryEmotion, emotion.Intensity);
        }

        private void UpdateSuggestionDisplay(ResponseSuggestion[] suggestions)
        {
            if (_suggestionOverlay == null)
            {
                return;
            }

            if (suggestions == null || suggestions.Length == 0)
            {
                _suggestionOverlay.Clear();
                return;
            }

            _suggestionOverlay.UpdateSuggestions(suggestions);
        }

        #region Camera Management

        private void InitializeCamera()
        {
            if (_cameraPreviewTarget == null)
            {
                Debug.LogWarning("[MainScreen] CameraPreviewTarget is not assigned, skipping camera initialization");
                return;
            }

            // 既存のカメラプレビューコンポーネントを確認
            _cameraPreview = CameraPreviewFactory.GetExisting(_cameraPreviewTarget);

            if (_cameraPreview == null)
            {
                // 新規作成
                _cameraPreview = CameraPreviewFactory.Create(_cameraPreviewType, _cameraPreviewTarget);
            }

            if (_cameraPreview != null)
            {
                _cameraPreview.Initialize();
            }
        }

        private void StartCamera()
        {
            if (_cameraPreview != null)
            {
                _cameraPreview.StartPreview();
            }
        }

        private void DisposeCamera()
        {
            if (_cameraPreview != null)
            {
                _cameraPreview.Dispose();
                _cameraPreview = null;
            }
        }

        /// <summary>
        /// カメラプレビューを一時停止する。
        /// </summary>
        public void PauseCamera()
        {
            if (_cameraPreview != null)
            {
                _cameraPreview.StopPreview();
            }
        }

        /// <summary>
        /// カメラプレビューを再開する。
        /// </summary>
        public void ResumeCamera()
        {
            if (_cameraPreview != null)
            {
                _cameraPreview.StartPreview();
            }
        }

        #endregion
    }
}
