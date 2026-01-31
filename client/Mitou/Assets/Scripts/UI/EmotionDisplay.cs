using UnityEngine;
using TMPro;
using ERA.Data;

namespace ERA.UI
{
    public class EmotionDisplay : MonoBehaviour
    {
        [SerializeField] private TextMeshProUGUI emotionIconText;
        [SerializeField] private TextMeshProUGUI descriptionText;
        [SerializeField] private TextMeshProUGUI suggestionText;
        [SerializeField] private GameObject suggestionContainer;

        [SerializeField] private float baseIconSize = 48f;

        public void UpdateEmotion(EmotionInterpretation emotion)
        {
            if (emotion == null)
            {
                ClearDisplay();
                return;
            }

            UpdateEmotionIcon(emotion.PrimaryEmotion, emotion.Intensity);
            UpdateDescription(emotion.Description);
            UpdateSuggestion(emotion.Suggestion);
        }

        private void UpdateEmotionIcon(string primaryEmotion, string intensity)
        {
            if (emotionIconText == null)
            {
                return;
            }

            var icon = EmotionMapper.GetIcon(primaryEmotion);
            var scale = EmotionMapper.GetIntensityScale(intensity);

            emotionIconText.text = icon;
            emotionIconText.fontSize = baseIconSize * scale;
        }

        private void UpdateDescription(string description)
        {
            if (descriptionText == null)
            {
                return;
            }

            descriptionText.text = description ?? string.Empty;
        }

        private void UpdateSuggestion(string suggestion)
        {
            if (suggestionText == null || suggestionContainer == null)
            {
                return;
            }

            var hasSuggestion = !string.IsNullOrEmpty(suggestion);
            suggestionContainer.SetActive(hasSuggestion);

            if (hasSuggestion)
            {
                suggestionText.text = suggestion;
            }
        }

        private void ClearDisplay()
        {
            if (emotionIconText != null)
            {
                emotionIconText.text = string.Empty;
            }

            if (descriptionText != null)
            {
                descriptionText.text = string.Empty;
            }

            if (suggestionContainer != null)
            {
                suggestionContainer.SetActive(false);
            }
        }
    }
}
