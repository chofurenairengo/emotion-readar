using UnityEngine;

namespace ERA.Camera
{
    /// <summary>
    /// カメラプレビュータイプ。
    /// </summary>
    public enum CameraPreviewType
    {
        /// <summary>Quest用（Passthrough API）</summary>
        QuestPassthrough,
        /// <summary>AR用（透過レンズ）</summary>
        ARTransparent,
        /// <summary>ARCore用（AR Foundation）</summary>
        ARCore,
        /// <summary>自動検出</summary>
        Auto
    }

    /// <summary>
    /// プラットフォーム別カメラプレビュー実装のファクトリ。
    /// </summary>
    public static class CameraPreviewFactory
    {
        /// <summary>
        /// 指定されたタイプのカメラプレビューコンポーネントを作成する。
        /// </summary>
        /// <param name="type">カメラプレビュータイプ</param>
        /// <param name="target">コンポーネントを追加するGameObject</param>
        /// <returns>作成されたICameraPreviewインスタンス</returns>
        public static ICameraPreview Create(CameraPreviewType type, GameObject target)
        {
            if (target == null)
            {
                Debug.LogError("[CameraPreviewFactory] Target GameObject is null");
                return null;
            }

            var actualType = type == CameraPreviewType.Auto ? DetectPlatform() : type;

            ICameraPreview preview = actualType switch
            {
                CameraPreviewType.QuestPassthrough => target.AddComponent<QuestPassthroughPreview>(),
                CameraPreviewType.ARTransparent => target.AddComponent<ARCameraPreview>(),
                CameraPreviewType.ARCore => target.AddComponent<ARCoreCameraPreview>(),
                _ => target.AddComponent<ARCoreCameraPreview>()
            };

            Debug.Log($"[CameraPreviewFactory] Created {actualType} camera preview");
            return preview;
        }

        /// <summary>
        /// 既存のカメラプレビューコンポーネントを取得する。
        /// </summary>
        /// <param name="target">検索対象のGameObject</param>
        /// <returns>見つかったICameraPreviewインスタンス、なければnull</returns>
        public static ICameraPreview GetExisting(GameObject target)
        {
            if (target == null)
            {
                return null;
            }

            // 各実装タイプを順に検索
            var quest = target.GetComponent<QuestPassthroughPreview>();
            if (quest != null) return quest;

            var ar = target.GetComponent<ARCameraPreview>();
            if (ar != null) return ar;

            var arCore = target.GetComponent<ARCoreCameraPreview>();
            if (arCore != null) return arCore;

            return null;
        }

        /// <summary>
        /// 実行環境に応じて適切なカメラプレビュータイプを検出する。
        /// </summary>
        /// <returns>検出されたカメラプレビュータイプ</returns>
        public static CameraPreviewType DetectPlatform()
        {
#if UNITY_EDITOR
            // エディタ上ではARCoreとして動作
            return CameraPreviewType.ARCore;
#elif UNITY_ANDROID
            // Quest検出（OVR SDKが存在する場合）
            if (IsQuestDevice())
            {
                return CameraPreviewType.QuestPassthrough;
            }

            // 通常AndroidはARCoreを使用
            return CameraPreviewType.ARCore;
#elif UNITY_WSA
            // HoloLens等のWindows MRデバイス
            return CameraPreviewType.ARTransparent;
#else
            // その他のプラットフォームはデフォルトでARCore
            return CameraPreviewType.ARCore;
#endif
        }

        private static bool IsQuestDevice()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            // 実際のデバイスモデルでQuest端末かどうか判定
            var deviceModel = SystemInfo.deviceModel.ToLower();
            var deviceName = SystemInfo.deviceName.ToLower();

            // Questデバイスのモデル名をチェック
            bool isQuestHardware = deviceModel.Contains("quest") ||
                                   deviceModel.Contains("oculus") ||
                                   deviceName.Contains("quest");

            if (!isQuestHardware)
            {
                Debug.Log($"[CameraPreviewFactory] Not a Quest device: {SystemInfo.deviceModel}");
                return false;
            }

            Debug.Log($"[CameraPreviewFactory] Quest hardware detected: {SystemInfo.deviceModel}");

            // OVRPluginが存在するかチェック
            try
            {
                var ovrManagerType = System.Type.GetType("OVRManager, Assembly-CSharp");
                if (ovrManagerType != null)
                {
                    var instance = Object.FindObjectOfType(ovrManagerType);
                    return instance != null;
                }
            }
            catch
            {
                // OVR SDKが存在しない場合は無視
            }
#endif
            return false;
        }

    }
}
