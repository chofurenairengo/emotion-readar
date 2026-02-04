using UnityEngine;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.Management;

namespace ERA.Camera
{
    /// <summary>
    /// ARCore用カメラプレビュー実装。
    /// AR FoundationのAR Camera Managerを使用してカメラ映像をAR背景として表示。
    /// </summary>
    public class ARCoreCameraPreview : MonoBehaviour, ICameraPreview
    {
        [Header("AR Components")]
        [SerializeField] private ARSession _arSession;
        [SerializeField] private ARCameraManager _arCameraManager;
        [SerializeField] private ARCameraBackground _arCameraBackground;

        [Header("Auto Setup")]
        [SerializeField] private bool _autoSetupARComponents = true;

        private bool _isInitialized = false;

        public bool IsActive => _arSession != null &&
                                _arSession.enabled &&
                                ARSession.state >= ARSessionState.SessionTracking;

        public void Initialize()
        {
            if (_isInitialized)
            {
                Debug.LogWarning("[ARCoreCameraPreview] Already initialized");
                return;
            }

            if (_autoSetupARComponents)
            {
                SetupARComponents();
            }

            if (_arSession == null)
            {
                Debug.LogError("[ARCoreCameraPreview] AR Session is not assigned");
                return;
            }

            // XR Loaderの初期化を試みる
            InitializeARCoreLoader();

            _isInitialized = true;
            Debug.Log("[ARCoreCameraPreview] Initialized");
        }

        public void StartPreview()
        {
            if (!_isInitialized)
            {
                Debug.LogWarning("[ARCoreCameraPreview] Not initialized, call Initialize() first");
                Initialize();
            }

            if (_arSession != null)
            {
                _arSession.enabled = true;
            }

            if (_arCameraManager != null)
            {
                _arCameraManager.enabled = true;
            }

            if (_arCameraBackground != null)
            {
                _arCameraBackground.enabled = true;
            }

            Debug.Log("[ARCoreCameraPreview] Preview started");
        }

        public void StopPreview()
        {
            if (_arSession != null)
            {
                _arSession.enabled = false;
            }

            if (_arCameraManager != null)
            {
                _arCameraManager.enabled = false;
            }

            if (_arCameraBackground != null)
            {
                _arCameraBackground.enabled = false;
            }

            Debug.Log("[ARCoreCameraPreview] Preview stopped");
        }

        public void Dispose()
        {
            StopPreview();
            DeinitializeARCoreLoader();
            _isInitialized = false;
            Debug.Log("[ARCoreCameraPreview] Disposed");
        }

        private void SetupARComponents()
        {
            // AR Sessionの検索または作成
            if (_arSession == null)
            {
                _arSession = FindObjectOfType<ARSession>();
                if (_arSession == null)
                {
                    var sessionGO = new GameObject("AR Session");
                    _arSession = sessionGO.AddComponent<ARSession>();
                    sessionGO.AddComponent<ARInputManager>();
                    Debug.Log("[ARCoreCameraPreview] Created AR Session");
                }
            }

            // AR Camera Managerの検索
            if (_arCameraManager == null)
            {
                _arCameraManager = FindObjectOfType<ARCameraManager>();
                if (_arCameraManager == null)
                {
                    // メインカメラに追加を試みる
                    var mainCamera = UnityEngine.Camera.main;
                    if (mainCamera != null)
                    {
                        _arCameraManager = mainCamera.gameObject.AddComponent<ARCameraManager>();
                        Debug.Log("[ARCoreCameraPreview] Added AR Camera Manager to main camera");
                    }
                }
            }

            // AR Camera Backgroundの検索
            if (_arCameraBackground == null)
            {
                _arCameraBackground = FindObjectOfType<ARCameraBackground>();
                if (_arCameraBackground == null && _arCameraManager != null)
                {
                    _arCameraBackground = _arCameraManager.gameObject.AddComponent<ARCameraBackground>();
                    Debug.Log("[ARCoreCameraPreview] Added AR Camera Background");
                }
            }
        }

        private void InitializeARCoreLoader()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            var xrSettings = XRGeneralSettings.Instance;
            if (xrSettings == null || xrSettings.Manager == null)
            {
                Debug.LogWarning("[ARCoreCameraPreview] XR General Settings not found");
                return;
            }

            var manager = xrSettings.Manager;

            // 既に初期化済みの場合はスキップ
            if (manager.isInitializationComplete)
            {
                Debug.Log("[ARCoreCameraPreview] XR Manager already initialized");
                return;
            }

            // XRLoaderを初期化（ARCoreを含む利用可能なローダーを自動検出）
            Debug.Log("[ARCoreCameraPreview] Initializing XR Loader...");
            manager.InitializeLoaderSync();

            if (manager.isInitializationComplete)
            {
                manager.StartSubsystems();
                Debug.Log($"[ARCoreCameraPreview] XR Loader initialized: {manager.activeLoader?.name}");
            }
            else
            {
                Debug.LogError("[ARCoreCameraPreview] Failed to initialize XR Loader");
            }
#endif
        }

        private void DeinitializeARCoreLoader()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            var xrSettings = XRGeneralSettings.Instance;
            if (xrSettings?.Manager != null && xrSettings.Manager.isInitializationComplete)
            {
                xrSettings.Manager.StopSubsystems();
                xrSettings.Manager.DeinitializeLoader();
                Debug.Log("[ARCoreCameraPreview] ARCore Loader deinitialized");
            }
#endif
        }

        private void OnDestroy()
        {
            Dispose();
        }
    }
}
