using UnityEngine;
using TMPro;

namespace ERA.UI
{
    /// <summary>
    /// 感情アイコンを左上に表示するオーバーレイコンポーネント。
    /// MainScreenから呼び出され、サーバーから受信した感情データを表示する。
    /// </summary>
    public class EmotionOverlay : MonoBehaviour
    {
        [Header("UI References")]
        [SerializeField] private TextMeshProUGUI _emotionIconText;

        [Header("Settings")]
        [SerializeField] private float _baseIconSize = 64f;

        /// <summary>
        /// 感情データを更新して表示する。
        /// </summary>
        /// <param name="primaryEmotion">主要な感情（happy, sad, angry等）</param>
        /// <param name="intensity">感情の強度（low, medium, high）</param>
        public void UpdateEmotion(string primaryEmotion, string intensity = "medium")
        {
            if (_emotionIconText == null)
            {
                Debug.LogWarning("[EmotionOverlay] emotionIconText is not assigned");
                return;
            }

            var icon = EmotionMapper.GetIcon(primaryEmotion);
            var scale = EmotionMapper.GetIntensityScale(intensity);

            _emotionIconText.text = icon;
            _emotionIconText.fontSize = _baseIconSize * scale;
        }

        /// <summary>
        /// 表示をクリアしてデフォルトアイコンを表示する。
        /// </summary>
        public void Clear()
        {
            if (_emotionIconText == null)
            {
                return;
            }

            _emotionIconText.text = EmotionMapper.GetDefaultIcon();
            _emotionIconText.fontSize = _baseIconSize;
        }

        /// <summary>
        /// オーバーレイの表示/非表示を切り替える。
        /// </summary>
        /// <param name="visible">表示する場合はtrue</param>
        public void SetVisible(bool visible)
        {
            gameObject.SetActive(visible);
        }

        /// <summary>
        /// ランタイムでUI参照を設定する（UIAutoBuilderから呼び出し）。
        /// </summary>
        public void Setup(TextMeshProUGUI emotionIconText, float baseIconSize)
        {
            _emotionIconText = emotionIconText;
            _baseIconSize = baseIconSize;
        }

        private void Start()
        {
            if (_emotionIconText == null)
            {
                Debug.LogError("[EmotionOverlay] emotionIconText is not assigned");
            }
        }
    }
}
