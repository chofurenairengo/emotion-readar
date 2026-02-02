namespace ERA.Camera
{
    /// <summary>
    /// カメラプレビューの抽象インターフェース。
    /// プラットフォームごとに異なるカメラ実装を統一的に扱う。
    /// </summary>
    public interface ICameraPreview
    {
        /// <summary>
        /// カメラプレビューを初期化する。
        /// </summary>
        void Initialize();

        /// <summary>
        /// カメラプレビューを開始する。
        /// </summary>
        void StartPreview();

        /// <summary>
        /// カメラプレビューを停止する。
        /// </summary>
        void StopPreview();

        /// <summary>
        /// リソースを解放する。
        /// </summary>
        void Dispose();

        /// <summary>
        /// カメラプレビューがアクティブかどうか。
        /// </summary>
        bool IsActive { get; }
    }
}
