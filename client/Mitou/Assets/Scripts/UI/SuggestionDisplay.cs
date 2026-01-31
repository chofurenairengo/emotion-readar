using UnityEngine;
using TMPro;
using ERA.Data;

namespace ERA.UI
{
    public class SuggestionDisplay : MonoBehaviour
    {
        [SerializeField] private GameObject suggestion1Container;
        [SerializeField] private TextMeshProUGUI suggestion1Text;
        [SerializeField] private TextMeshProUGUI suggestion1IntentText;

        [SerializeField] private GameObject suggestion2Container;
        [SerializeField] private TextMeshProUGUI suggestion2Text;
        [SerializeField] private TextMeshProUGUI suggestion2IntentText;

        public void UpdateSuggestions(ResponseSuggestion[] suggestions)
        {
            if (suggestions == null || suggestions.Length == 0)
            {
                HideAllSuggestions();
                return;
            }

            UpdateSuggestion1(suggestions.Length > 0 ? suggestions[0] : null);
            UpdateSuggestion2(suggestions.Length > 1 ? suggestions[1] : null);
        }

        private void UpdateSuggestion1(ResponseSuggestion suggestion)
        {
            if (suggestion1Container == null)
            {
                return;
            }

            var hasSuggestion = suggestion != null;
            suggestion1Container.SetActive(hasSuggestion);

            if (hasSuggestion)
            {
                if (suggestion1Text != null)
                {
                    suggestion1Text.text = suggestion.Text ?? string.Empty;
                }

                if (suggestion1IntentText != null)
                {
                    suggestion1IntentText.text = suggestion.Intent ?? string.Empty;
                }
            }
        }

        private void UpdateSuggestion2(ResponseSuggestion suggestion)
        {
            if (suggestion2Container == null)
            {
                return;
            }

            var hasSuggestion = suggestion != null;
            suggestion2Container.SetActive(hasSuggestion);

            if (hasSuggestion)
            {
                if (suggestion2Text != null)
                {
                    suggestion2Text.text = suggestion.Text ?? string.Empty;
                }

                if (suggestion2IntentText != null)
                {
                    suggestion2IntentText.text = suggestion.Intent ?? string.Empty;
                }
            }
        }

        private void HideAllSuggestions()
        {
            if (suggestion1Container != null)
            {
                suggestion1Container.SetActive(false);
            }

            if (suggestion2Container != null)
            {
                suggestion2Container.SetActive(false);
            }
        }
    }
}
