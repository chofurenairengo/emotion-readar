using UnityEngine;
using UnityEngine.UI;
using ERA.Camera;

namespace ERA.UI
{
    /// <summary>
    /// Canvasのレンダリングモードをプラットフォームに応じて動的に切り替える。
    /// ARCore（通常Android）: Screen Space - Camera
    /// Quest 3（Passthrough）: World Space（適切な距離・スケール）
    /// </summary>
    [DefaultExecutionOrder(-100)]
    [RequireComponent(typeof(Canvas))]
    public class CanvasConfigurator : MonoBehaviour
    {
        [Header("Quest Settings (World Space)")]
        [SerializeField] private float _questDistance = 1.5f;
        [SerializeField] private float _questScale = 0.001f;

        [Header("ARCore Settings (Screen Space - Camera)")]
        [SerializeField] private float _arCorePlaneDistance = 0.5f;
        [SerializeField] private Vector2 _referenceResolution = new Vector2(1080f, 2400f);

        private Canvas _canvas;
        private RectTransform _rectTransform;

        private void Awake()
        {
            _canvas = GetComponent<Canvas>();
            _rectTransform = GetComponent<RectTransform>();

            ConfigureForPlatform();
        }

        private void ConfigureForPlatform()
        {
            var platform = CameraPreviewFactory.DetectPlatform();
            Debug.Log($"[CanvasConfigurator] Detected platform: {platform}");

            switch (platform)
            {
                case CameraPreviewType.QuestPassthrough:
                    ConfigureForQuest();
                    break;
                case CameraPreviewType.ARCore:
                case CameraPreviewType.ARTransparent:
                default:
                    ConfigureForARCore();
                    break;
            }
        }

        private void ConfigureForQuest()
        {
            Debug.Log("[CanvasConfigurator] Configuring Canvas for Quest (World Space)");

            _canvas.renderMode = RenderMode.WorldSpace;

            var mainCamera = UnityEngine.Camera.main;
            if (mainCamera != null)
            {
                _canvas.worldCamera = mainCamera;
            }

            _rectTransform.localPosition = new Vector3(0f, 0f, _questDistance);
            _rectTransform.localScale = new Vector3(_questScale, _questScale, _questScale);

            Debug.Log($"[CanvasConfigurator] Quest: distance={_questDistance}m, scale={_questScale}");
        }

        private void ConfigureForARCore()
        {
            Debug.Log("[CanvasConfigurator] Configuring Canvas for ARCore (Screen Space - Overlay)");

            // Screen Space - Overlay: カメラに依存せず常に最前面に描画
            // ARCameraBackgroundがカメラの描画パイプラインを変更するため、
            // Screen Space - Cameraだと描画が競合する
            _canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            _canvas.sortingOrder = 100;

            _rectTransform.localPosition = Vector3.zero;
            _rectTransform.localScale = Vector3.one;

            var scaler = GetComponent<CanvasScaler>();
            if (scaler != null)
            {
                scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
                scaler.referenceResolution = _referenceResolution;
                scaler.matchWidthOrHeight = 0.5f;
            }

            Debug.Log("[CanvasConfigurator] ARCore: Overlay mode, sortingOrder=100");
        }
    }
}
