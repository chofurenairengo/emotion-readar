using System.Collections.Generic;

namespace ERA.UI
{
    public static class EmotionMapper
    {
        private static readonly Dictionary<string, string> EmotionIcons = new Dictionary<string, string>
        {
            { "happy", "\U0001F60A" },      // 😊
            { "sad", "\U0001F622" },         // 😢
            { "angry", "\U0001F620" },       // 😠
            { "confused", "\U0001F615" },    // 😕
            { "surprised", "\U0001F632" },   // 😲
            { "neutral", "\U0001F610" },     // 😐
            { "fearful", "\U0001F628" },     // 😨
            { "disgusted", "\U0001F922" }    // 🤢
        };

        private static readonly Dictionary<string, float> IntensityScales = new Dictionary<string, float>
        {
            { "low", 0.8f },
            { "medium", 1.0f },
            { "high", 1.2f }
        };

        private const string DefaultIcon = "\u2753";  // ❓
        private const float DefaultScale = 1.0f;

        public static string GetIcon(string primaryEmotion)
        {
            if (string.IsNullOrEmpty(primaryEmotion))
            {
                return DefaultIcon;
            }

            return EmotionIcons.TryGetValue(primaryEmotion.ToLower(), out var icon)
                ? icon
                : DefaultIcon;
        }

        public static string GetDefaultIcon()
        {
            return DefaultIcon;
        }

        public static float GetIntensityScale(string intensity)
        {
            if (string.IsNullOrEmpty(intensity))
            {
                return DefaultScale;
            }

            return IntensityScales.TryGetValue(intensity.ToLower(), out var scale)
                ? scale
                : DefaultScale;
        }
    }
}
