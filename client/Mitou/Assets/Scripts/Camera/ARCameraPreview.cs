using UnityEngine;

namespace ERA.Camera
{
    /// <summary>
    /// AR用（透過レンズ対応）カメラプレビュー実装。
    /// HoloLens等の透過型ARデバイス用のスタブ実装。
    /// 透過レンズでは実世界が直接見えるため、カメラプレビューは不要。
    /// </summary>
    public class ARCameraPreview : MonoBehaviour, ICameraPreview
    {
        private bool _isActive = false;

        public bool IsActive => _isActive;

        public void Initialize()
        {
            // 透過レンズはカメラプレビュー不要
            Debug.Log("[ARCameraPreview] Initialized (no-op for transparent lens AR)");
        }

        public void StartPreview()
        {
            // 透過レンズはカメラプレビュー不要
            _isActive = true;
            Debug.Log("[ARCameraPreview] Preview started (no-op for transparent lens AR)");
        }

        public void StopPreview()
        {
            // 透過レンズはカメラプレビュー不要
            _isActive = false;
            Debug.Log("[ARCameraPreview] Preview stopped (no-op for transparent lens AR)");
        }

        public void Dispose()
        {
            _isActive = false;
            Debug.Log("[ARCameraPreview] Disposed (no-op for transparent lens AR)");
        }
    }
}
