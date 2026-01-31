using UnityEngine;
using TMPro;
using ERA.Data;

namespace ERA.UI
{
    public class HUDController : MonoBehaviour
    {
        [SerializeField] private EmotionDisplay emotionDisplay;
        [SerializeField] private SuggestionDisplay suggestionDisplay;
        [SerializeField] private TextMeshProUGUI situationAnalysisText;
        [SerializeField] private GameObject situationAnalysisContainer;

        public void HandleAnalysisResponse(AnalysisResponse response)
        {
            if (response == null)
            {
                ClearHUD();
                return;
            }

            UpdateEmotionDisplay(response.Emotion);
            UpdateSuggestionDisplay(response.Suggestions);
            UpdateSituationAnalysis(response.SituationAnalysis);
        }

        private void UpdateEmotionDisplay(EmotionInterpretation emotion)
        {
            if (emotionDisplay == null)
            {
                return;
            }

            emotionDisplay.UpdateEmotion(emotion);
        }

        private void UpdateSuggestionDisplay(ResponseSuggestion[] suggestions)
        {
            if (suggestionDisplay == null)
            {
                return;
            }

            suggestionDisplay.UpdateSuggestions(suggestions);
        }

        private void UpdateSituationAnalysis(string situationAnalysis)
        {
            if (situationAnalysisText == null || situationAnalysisContainer == null)
            {
                return;
            }

            var hasAnalysis = !string.IsNullOrEmpty(situationAnalysis);
            situationAnalysisContainer.SetActive(hasAnalysis);

            if (hasAnalysis)
            {
                situationAnalysisText.text = situationAnalysis;
            }
        }

        private void ClearHUD()
        {
            if (emotionDisplay != null)
            {
                emotionDisplay.UpdateEmotion(null);
            }

            if (suggestionDisplay != null)
            {
                suggestionDisplay.UpdateSuggestions(null);
            }

            if (situationAnalysisContainer != null)
            {
                situationAnalysisContainer.SetActive(false);
            }
        }
    }
}
