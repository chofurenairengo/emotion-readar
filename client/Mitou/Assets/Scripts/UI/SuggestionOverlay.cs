using UnityEngine;
using TMPro;
using ERA.Data;

namespace ERA.UI
{
    /// <summary>
    /// 提案文を中央下部に表示するオーバーレイコンポーネント。
    /// MainScreenから呼び出され、サーバーから受信した提案文を最大2件表示する。
    /// </summary>
    public class SuggestionOverlay : MonoBehaviour
    {
        [Header("UI References")]
        [SerializeField] private TextMeshProUGUI _suggestion1Text;
        [SerializeField] private TextMeshProUGUI _suggestion2Text;

        [Header("Containers")]
        [SerializeField] private GameObject _suggestion1Container;
        [SerializeField] private GameObject _suggestion2Container;

        /// <summary>
        /// 提案文を更新して表示する。
        /// </summary>
        /// <param name="suggestions">提案文の配列（最大2件表示）</param>
        public void UpdateSuggestions(ResponseSuggestion[] suggestions)
        {
            if (suggestions == null || suggestions.Length == 0)
            {
                HideAll();
                return;
            }

            UpdateSuggestion1(suggestions.Length > 0 ? suggestions[0] : null);
            UpdateSuggestion2(suggestions.Length > 1 ? suggestions[1] : null);
        }

        /// <summary>
        /// 文字列配列で提案文を更新する。
        /// </summary>
        /// <param name="suggestionTexts">提案文の文字列配列</param>
        public void UpdateSuggestions(string[] suggestionTexts)
        {
            if (suggestionTexts == null || suggestionTexts.Length == 0)
            {
                HideAll();
                return;
            }

            UpdateSuggestionText(_suggestion1Container, _suggestion1Text,
                suggestionTexts.Length > 0 ? suggestionTexts[0] : null);
            UpdateSuggestionText(_suggestion2Container, _suggestion2Text,
                suggestionTexts.Length > 1 ? suggestionTexts[1] : null);
        }

        /// <summary>
        /// すべての提案文表示をクリアする。
        /// </summary>
        public void Clear()
        {
            HideAll();
        }

        /// <summary>
        /// オーバーレイの表示/非表示を切り替える。
        /// </summary>
        /// <param name="visible">表示する場合はtrue</param>
        public void SetVisible(bool visible)
        {
            gameObject.SetActive(visible);
        }

        private void UpdateSuggestion1(ResponseSuggestion suggestion)
        {
            if (_suggestion1Container == null)
            {
                return;
            }

            var hasSuggestion = suggestion != null && !string.IsNullOrEmpty(suggestion.Text);
            _suggestion1Container.SetActive(hasSuggestion);

            if (hasSuggestion && _suggestion1Text != null)
            {
                _suggestion1Text.text = suggestion.Text;
            }
        }

        private void UpdateSuggestion2(ResponseSuggestion suggestion)
        {
            if (_suggestion2Container == null)
            {
                return;
            }

            var hasSuggestion = suggestion != null && !string.IsNullOrEmpty(suggestion.Text);
            _suggestion2Container.SetActive(hasSuggestion);

            if (hasSuggestion && _suggestion2Text != null)
            {
                _suggestion2Text.text = suggestion.Text;
            }
        }

        private void UpdateSuggestionText(GameObject container, TextMeshProUGUI textComponent, string text)
        {
            if (container == null)
            {
                return;
            }

            var hasText = !string.IsNullOrEmpty(text);
            container.SetActive(hasText);

            if (hasText && textComponent != null)
            {
                textComponent.text = text;
            }
        }

        private void HideAll()
        {
            if (_suggestion1Container != null)
            {
                _suggestion1Container.SetActive(false);
            }

            if (_suggestion2Container != null)
            {
                _suggestion2Container.SetActive(false);
            }
        }

        /// <summary>
        /// ランタイムでUI参照を設定する（UIAutoBuilderから呼び出し）。
        /// </summary>
        public void Setup(TextMeshProUGUI s1Text, TextMeshProUGUI s2Text,
                          GameObject s1Container, GameObject s2Container)
        {
            _suggestion1Text = s1Text;
            _suggestion2Text = s2Text;
            _suggestion1Container = s1Container;
            _suggestion2Container = s2Container;
        }

        private void Start()
        {
            if (_suggestion1Text == null)
            {
                Debug.LogWarning("[SuggestionOverlay] suggestion1Text is not assigned");
            }

            if (_suggestion2Text == null)
            {
                Debug.LogWarning("[SuggestionOverlay] suggestion2Text is not assigned");
            }
        }
    }
}
