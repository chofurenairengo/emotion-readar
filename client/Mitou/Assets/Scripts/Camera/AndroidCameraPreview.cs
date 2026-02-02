using UnityEngine;
using UnityEngine.UI;

namespace ERA.Camera
{
    /// <summary>
    /// Android用カメラプレビュー実装。
    /// WebCamTextureを使用してカメラ映像を表示する。
    /// </summary>
    public class AndroidCameraPreview : MonoBehaviour, ICameraPreview
    {
        [Header("UI References")]
        [SerializeField] private RawImage _previewImage;

        [Header("Camera Settings")]
        [SerializeField] private bool _useFrontCamera = true;
        [SerializeField] private int _requestedWidth = 1280;
        [SerializeField] private int _requestedHeight = 720;
        [SerializeField] private int _requestedFPS = 30;

        private WebCamTexture _webCamTexture;
        private bool _isInitialized = false;

        public bool IsActive => _webCamTexture != null && _webCamTexture.isPlaying;

        public void Initialize()
        {
            if (_isInitialized)
            {
                Debug.LogWarning("[AndroidCameraPreview] Already initialized");
                return;
            }

            if (_previewImage == null)
            {
                Debug.LogError("[AndroidCameraPreview] PreviewImage is not assigned");
                return;
            }

            var deviceName = GetCameraDeviceName();
            if (string.IsNullOrEmpty(deviceName))
            {
                Debug.LogError("[AndroidCameraPreview] No camera device found");
                return;
            }

            _webCamTexture = new WebCamTexture(deviceName, _requestedWidth, _requestedHeight, _requestedFPS);
            _previewImage.texture = _webCamTexture;

            // カメラの向きに合わせてRawImageを調整
            AdjustPreviewOrientation();

            _isInitialized = true;
            Debug.Log($"[AndroidCameraPreview] Initialized with device: {deviceName}");
        }

        public void StartPreview()
        {
            if (!_isInitialized)
            {
                Debug.LogWarning("[AndroidCameraPreview] Not initialized, call Initialize() first");
                Initialize();
            }

            if (_webCamTexture != null && !_webCamTexture.isPlaying)
            {
                _webCamTexture.Play();
                Debug.Log("[AndroidCameraPreview] Preview started");
            }
        }

        public void StopPreview()
        {
            if (_webCamTexture != null && _webCamTexture.isPlaying)
            {
                _webCamTexture.Stop();
                Debug.Log("[AndroidCameraPreview] Preview stopped");
            }
        }

        public void Dispose()
        {
            StopPreview();

            if (_webCamTexture != null)
            {
                Destroy(_webCamTexture);
                _webCamTexture = null;
            }

            if (_previewImage != null)
            {
                _previewImage.texture = null;
            }

            _isInitialized = false;
            Debug.Log("[AndroidCameraPreview] Disposed");
        }

        private string GetCameraDeviceName()
        {
            var devices = WebCamTexture.devices;

            if (devices.Length == 0)
            {
                return null;
            }

            // フロントカメラを優先
            if (_useFrontCamera)
            {
                foreach (var device in devices)
                {
                    if (device.isFrontFacing)
                    {
                        return device.name;
                    }
                }
            }
            else
            {
                // バックカメラを優先
                foreach (var device in devices)
                {
                    if (!device.isFrontFacing)
                    {
                        return device.name;
                    }
                }
            }

            // 見つからなければ最初のデバイスを使用
            return devices[0].name;
        }

        private void AdjustPreviewOrientation()
        {
            if (_previewImage == null || _webCamTexture == null)
            {
                return;
            }

            // フロントカメラの場合は左右反転
            if (_useFrontCamera)
            {
                _previewImage.rectTransform.localScale = new Vector3(-1, 1, 1);
            }
            else
            {
                _previewImage.rectTransform.localScale = Vector3.one;
            }
        }

        private void OnDestroy()
        {
            Dispose();
        }
    }
}
