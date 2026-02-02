using UnityEngine;

namespace ERA.Camera
{
    /// <summary>
    /// Quest 3用Passthroughプレビュー実装。
    /// OVR Passthrough APIを使用して現実世界を表示する。
    /// </summary>
    public class QuestPassthroughPreview : MonoBehaviour, ICameraPreview
    {
        private bool _isInitialized = false;
        private bool _isActive = false;

        public bool IsActive => _isActive;

        public void Initialize()
        {
            if (_isInitialized)
            {
                Debug.LogWarning("[QuestPassthroughPreview] Already initialized");
                return;
            }

#if UNITY_ANDROID && !UNITY_EDITOR
            InitializePassthrough();
#else
            Debug.Log("[QuestPassthroughPreview] Passthrough is only available on Quest devices");
#endif

            _isInitialized = true;
        }

        public void StartPreview()
        {
            if (!_isInitialized)
            {
                Initialize();
            }

#if UNITY_ANDROID && !UNITY_EDITOR
            EnablePassthrough();
#endif

            _isActive = true;
            Debug.Log("[QuestPassthroughPreview] Passthrough started");
        }

        public void StopPreview()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            DisablePassthrough();
#endif

            _isActive = false;
            Debug.Log("[QuestPassthroughPreview] Passthrough stopped");
        }

        public void Dispose()
        {
            StopPreview();
            _isInitialized = false;
            Debug.Log("[QuestPassthroughPreview] Disposed");
        }

#if UNITY_ANDROID && !UNITY_EDITOR
        private void InitializePassthrough()
        {
            // OVRManager.instance が存在する場合、Passthrough設定を行う
            // 注意: OVRManagerはシーンに配置されている必要がある
            var ovrManager = FindObjectOfType<OVRManager>();
            if (ovrManager != null)
            {
                // Passthroughを有効化
                ovrManager.isInsightPassthroughEnabled = true;
                Debug.Log("[QuestPassthroughPreview] OVRManager Passthrough enabled");
            }
            else
            {
                Debug.LogWarning("[QuestPassthroughPreview] OVRManager not found in scene");
            }
        }

        private void EnablePassthrough()
        {
            // OVRPassthroughLayerを有効化
            var passthroughLayer = GetComponent<OVRPassthroughLayer>();
            if (passthroughLayer == null)
            {
                passthroughLayer = gameObject.AddComponent<OVRPassthroughLayer>();
                passthroughLayer.projectionSurfaceType = OVRPassthroughLayer.ProjectionSurfaceType.Reconstructed;
            }
            passthroughLayer.hidden = false;
        }

        private void DisablePassthrough()
        {
            var passthroughLayer = GetComponent<OVRPassthroughLayer>();
            if (passthroughLayer != null)
            {
                passthroughLayer.hidden = true;
            }
        }
#endif

        private void OnDestroy()
        {
            Dispose();
        }
    }
}
