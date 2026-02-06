using System.Collections;
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
        private bool _isInitializing = false;
        private Coroutine _initCoroutine;
        private Coroutine _startPreviewCoroutine;
        private XRGeneralSettings _cachedXRSettings;

        public bool IsActive => _arSession != null &&
                                _arSession.enabled &&
                                ARSession.state >= ARSessionState.SessionTracking;

        private void OnEnable()
        {
            ARSession.stateChanged += OnARSessionStateChanged;
        }

        private void OnDisable()
        {
            ARSession.stateChanged -= OnARSessionStateChanged;
        }

        private void OnARSessionStateChanged(ARSessionStateChangedEventArgs args)
        {
            Debug.Log($"[ARCoreCameraPreview] ARSession state changed: {args.state}");

            switch (args.state)
            {
                case ARSessionState.SessionTracking:
                    Debug.Log("[ARCoreCameraPreview] AR tracking started successfully");
                    break;
                case ARSessionState.SessionInitializing:
                    Debug.Log("[ARCoreCameraPreview] AR session initializing...");
                    break;
                case ARSessionState.Unsupported:
                    Debug.LogError("[ARCoreCameraPreview] AR is not supported on this device");
                    break;
                case ARSessionState.NeedsInstall:
                    Debug.LogWarning("[ARCoreCameraPreview] ARCore needs to be installed");
                    break;
            }
        }

        public void Initialize()
        {
            if (_isInitialized)
            {
                Debug.LogWarning("[ARCoreCameraPreview] Already initialized");
                return;
            }

            if (_isInitializing)
            {
                Debug.LogWarning("[ARCoreCameraPreview] Initialization already in progress");
                return;
            }

            _initCoroutine = StartCoroutine(InitializeAsync());
        }

        private IEnumerator InitializeAsync()
        {
            _isInitializing = true;
            Debug.Log("[ARCoreCameraPreview] Starting async initialization...");

            // XRローダー初期化を先に実行
            yield return StartCoroutine(InitializeARCoreLoaderAsync());

            // 初期化成功後にARコンポーネントをセットアップ
            if (_autoSetupARComponents)
            {
                SetupARComponents();
            }

            // カメラのクリアフラグを透明に設定（AR背景が正しく描画されるようにする）
            var mainCamera = UnityEngine.Camera.main;
            if (mainCamera != null)
            {
                mainCamera.clearFlags = CameraClearFlags.SolidColor;
                mainCamera.backgroundColor = new Color(0f, 0f, 0f, 0f);
                Debug.Log("[ARCoreCameraPreview] Camera clear flags set for AR background");
            }

            if (_arSession == null)
            {
                Debug.LogError("[ARCoreCameraPreview] AR Session is not assigned after setup");
                _isInitializing = false;
                yield break;
            }

            _isInitialized = true;
            _isInitializing = false;
            Debug.Log("[ARCoreCameraPreview] Async initialization completed");
        }

        public void StartPreview()
        {
            if (!_isInitialized && !_isInitializing)
            {
                Debug.LogWarning("[ARCoreCameraPreview] Not initialized, starting initialization first");
                Initialize();
            }

            if (_isInitializing)
            {
                if (_startPreviewCoroutine == null)
                {
                    Debug.Log("[ARCoreCameraPreview] Waiting for initialization to complete before starting preview");
                    _startPreviewCoroutine = StartCoroutine(StartPreviewWhenReady());
                }
                return;
            }

            EnablePreviewComponents();
        }

        private IEnumerator StartPreviewWhenReady()
        {
            float timeout = 10f;
            float elapsed = 0f;

            while (_isInitializing && elapsed < timeout)
            {
                yield return null;
                elapsed += Time.deltaTime;
            }

            _startPreviewCoroutine = null;

            if (elapsed >= timeout)
            {
                Debug.LogError("[ARCoreCameraPreview] Initialization timeout");
                yield break;
            }

            if (_isInitialized)
            {
                EnablePreviewComponents();
            }
        }

        private void EnablePreviewComponents()
        {
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
            if (_initCoroutine != null)
            {
                StopCoroutine(_initCoroutine);
                _initCoroutine = null;
            }

            if (_startPreviewCoroutine != null)
            {
                StopCoroutine(_startPreviewCoroutine);
                _startPreviewCoroutine = null;
            }

            StopPreview();
            DeinitializeARCoreLoader();
            _isInitialized = false;
            _isInitializing = false;
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

        private IEnumerator InitializeARCoreLoaderAsync()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Debug.Log("[ARCoreCameraPreview] Attempting to initialize XR Loader...");

            // XRGeneralSettingsのロード（Instanceがnullの場合はResourcesから試行）
            var xrSettings = XRGeneralSettings.Instance;
            if (xrSettings == null)
            {
                Debug.Log("[ARCoreCameraPreview] XRGeneralSettings.Instance is null, attempting to load from Resources...");
                xrSettings = Resources.Load<XRGeneralSettings>("XR/XRGeneralSettings");
                if (xrSettings != null)
                {
                    Debug.Log("[ARCoreCameraPreview] Loaded XRGeneralSettings from Resources");
                }
            }

            if (xrSettings == null)
            {
                Debug.LogError("[ARCoreCameraPreview] XRGeneralSettings not found (Instance and Resources both failed)");
                yield break;
            }

            if (xrSettings.Manager == null)
            {
                Debug.LogError("[ARCoreCameraPreview] XRManagerSettings is null");
                yield break;
            }

            // XRSettingsをキャッシュ（Deinitialize時に使用）
            _cachedXRSettings = xrSettings;

            var manager = xrSettings.Manager;
            Debug.Log($"[ARCoreCameraPreview] XR Manager found. Loaders count: {manager.activeLoaders?.Count ?? 0}");

            // 既に初期化済みの場合
            if (manager.isInitializationComplete)
            {
                if (manager.activeLoader != null)
                {
                    Debug.Log($"[ARCoreCameraPreview] XR Manager already initialized with: {manager.activeLoader.name}");
                }
                else
                {
                    Debug.LogWarning("[ARCoreCameraPreview] XR Manager initialized but no active loader");
                }
                yield break;
            }

            // 非同期初期化を開始
            Debug.Log("[ARCoreCameraPreview] Starting XR Loader initialization...");
            yield return manager.InitializeLoader();

            // 初期化結果を確認
            if (manager.isInitializationComplete)
            {
                if (manager.activeLoader != null)
                {
                    manager.StartSubsystems();
                    Debug.Log($"[ARCoreCameraPreview] XR Loader initialized successfully: {manager.activeLoader.name}");
                }
                else
                {
                    Debug.LogWarning("[ARCoreCameraPreview] XR initialization complete but no active loader found");
                }
            }
            else
            {
                Debug.LogError("[ARCoreCameraPreview] Failed to initialize XR Loader");
            }
#else
            Debug.Log("[ARCoreCameraPreview] Skipping XR Loader initialization (not Android runtime)");
            yield return null;
#endif
        }

        private void DeinitializeARCoreLoader()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            var xrSettings = _cachedXRSettings ?? XRGeneralSettings.Instance;
            if (xrSettings?.Manager != null && xrSettings.Manager.isInitializationComplete)
            {
                xrSettings.Manager.StopSubsystems();
                xrSettings.Manager.DeinitializeLoader();
                Debug.Log("[ARCoreCameraPreview] ARCore Loader deinitialized");
            }
            _cachedXRSettings = null;
#endif
        }

        private void OnDestroy()
        {
            Dispose();
        }
    }
}
