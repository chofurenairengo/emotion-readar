using UnityEngine;
using UnityEngine.UI;
using TMPro;

namespace ERA.UI
{
    /// <summary>
    /// Canvas配下にUI要素を自動生成する。
    /// EmotionOverlay（左上）とSuggestionOverlay（下部中央）を
    /// ランタイムで構築し、MainScreenに接続する。
    ///
    /// 初期化順序:
    /// 1. UIAutoBuilder.Awake (-200): UI GameObjectを生成、Setup()で参照を注入
    /// 2. CanvasConfigurator.Awake (-100): Canvasのレンダリングモードを設定
    /// 3. 各コンポーネントのStart(): バリデーション・初期化
    /// </summary>
    [DefaultExecutionOrder(-200)]
    [RequireComponent(typeof(Canvas))]
    public class UIAutoBuilder : MonoBehaviour
    {
        [Header("Colors")]
        [SerializeField] private Color _panelColor = new Color(0f, 0f, 0f, 0.5f);
        [SerializeField] private Color _textColor = Color.white;

        [Header("Emotion Overlay (Top-Left)")]
        [SerializeField] private float _emotionFontSize = 48f;
        [SerializeField] private Vector2 _emotionPanelSize = new Vector2(80f, 80f);

        [Header("Suggestion Overlay (Bottom-Center)")]
        [SerializeField] private float _suggestionFontSize = 24f;
        [SerializeField] private Vector2 _suggestionPanelSize = new Vector2(600f, 60f);
        [SerializeField] private float _suggestionSpacing = 10f;

        private void Awake()
        {
            BuildUI();
        }

        private void BuildUI()
        {
            Debug.Log("[UIAutoBuilder] Building UI elements...");

            var emotionOverlay = BuildEmotionOverlay();
            var suggestionOverlay = BuildSuggestionOverlay();

            WireMainScreen(emotionOverlay, suggestionOverlay);

            Debug.Log("[UIAutoBuilder] UI build complete");
        }

        private EmotionOverlay BuildEmotionOverlay()
        {
            var panelGO = CreatePanel("EmotionOverlayPanel", _emotionPanelSize);
            var rect = panelGO.GetComponent<RectTransform>();

            // Anchor top-left
            rect.anchorMin = new Vector2(0f, 1f);
            rect.anchorMax = new Vector2(0f, 1f);
            rect.pivot = new Vector2(0f, 1f);
            rect.anchoredPosition = new Vector2(20f, -20f);

            // Emotion icon text
            var textGO = CreateTMPText("EmotionIconText", panelGO.transform);
            var tmpText = textGO.GetComponent<TextMeshProUGUI>();
            tmpText.fontSize = _emotionFontSize;
            tmpText.alignment = TextAlignmentOptions.Center;
            tmpText.text = EmotionMapper.GetDefaultIcon();

            var textRect = textGO.GetComponent<RectTransform>();
            StretchToParent(textRect);

            // Add EmotionOverlay component and wire references via Setup()
            var overlay = panelGO.AddComponent<EmotionOverlay>();
            overlay.Setup(tmpText, _emotionFontSize);

            Debug.Log("[UIAutoBuilder] EmotionOverlay created (top-left)");
            return overlay;
        }

        private SuggestionOverlay BuildSuggestionOverlay()
        {
            var rootGO = new GameObject("SuggestionOverlayRoot");
            rootGO.transform.SetParent(transform, false);

            var rootRect = rootGO.AddComponent<RectTransform>();
            // Anchor bottom-center
            rootRect.anchorMin = new Vector2(0.5f, 0f);
            rootRect.anchorMax = new Vector2(0.5f, 0f);
            rootRect.pivot = new Vector2(0.5f, 0f);
            rootRect.anchoredPosition = new Vector2(0f, 20f);
            rootRect.sizeDelta = new Vector2(_suggestionPanelSize.x, _suggestionPanelSize.y * 2 + _suggestionSpacing);

            // Vertical Layout
            var layout = rootGO.AddComponent<VerticalLayoutGroup>();
            layout.spacing = _suggestionSpacing;
            layout.childAlignment = TextAnchor.LowerCenter;
            layout.childControlWidth = true;
            layout.childControlHeight = true;
            layout.childForceExpandWidth = true;
            layout.childForceExpandHeight = false;

            // Suggestion panels
            var s1Container = CreateSuggestionPanel("Suggestion1Container", rootGO.transform);
            var s1Text = s1Container.GetComponentInChildren<TextMeshProUGUI>();

            var s2Container = CreateSuggestionPanel("Suggestion2Container", rootGO.transform);
            var s2Text = s2Container.GetComponentInChildren<TextMeshProUGUI>();

            // Initially hidden
            s1Container.SetActive(false);
            s2Container.SetActive(false);

            // Add SuggestionOverlay component and wire references via Setup()
            var overlay = rootGO.AddComponent<SuggestionOverlay>();
            overlay.Setup(s1Text, s2Text, s1Container, s2Container);

            Debug.Log("[UIAutoBuilder] SuggestionOverlay created (bottom-center)");
            return overlay;
        }

        private GameObject CreateSuggestionPanel(string name, Transform parent)
        {
            var panel = CreatePanel(name, _suggestionPanelSize, parent);

            var textGO = CreateTMPText("Text", panel.transform);
            var tmpText = textGO.GetComponent<TextMeshProUGUI>();
            tmpText.fontSize = _suggestionFontSize;
            tmpText.alignment = TextAlignmentOptions.Center;
            tmpText.text = "";

            var textRect = textGO.GetComponent<RectTransform>();
            StretchToParent(textRect);
            textRect.offsetMin = new Vector2(10f, 5f);
            textRect.offsetMax = new Vector2(-10f, -5f);

            var layoutElement = panel.AddComponent<LayoutElement>();
            layoutElement.preferredHeight = _suggestionPanelSize.y;

            return panel;
        }

        private GameObject CreatePanel(string name, Vector2 size, Transform parent = null)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent != null ? parent : transform, false);

            var rect = go.AddComponent<RectTransform>();
            rect.sizeDelta = size;

            var image = go.AddComponent<Image>();
            image.color = _panelColor;

            return go;
        }

        private GameObject CreateTMPText(string name, Transform parent)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);

            go.AddComponent<RectTransform>();

            var tmp = go.AddComponent<TextMeshProUGUI>();
            tmp.color = _textColor;
            tmp.enableAutoSizing = false;

            return go;
        }

        private void StretchToParent(RectTransform rect)
        {
            rect.anchorMin = Vector2.zero;
            rect.anchorMax = Vector2.one;
            rect.offsetMin = Vector2.zero;
            rect.offsetMax = Vector2.zero;
        }

        private void WireMainScreen(EmotionOverlay emotionOverlay, SuggestionOverlay suggestionOverlay)
        {
            var mainScreen = GetComponent<MainScreen>();
            if (mainScreen == null)
            {
                mainScreen = gameObject.AddComponent<MainScreen>();
                Debug.Log("[UIAutoBuilder] MainScreen component added");
            }

            // 既存のARCoreCameraPreviewがあればカメラターゲットとして渡す
            var existingPreview = GetComponent<ERA.Camera.ARCoreCameraPreview>();
            var cameraTarget = existingPreview != null ? gameObject : null;

            mainScreen.SetupOverlays(emotionOverlay, suggestionOverlay, cameraTarget);

            if (cameraTarget != null)
            {
                Debug.Log("[UIAutoBuilder] MainScreen wired with overlays + camera target");
            }
            else
            {
                Debug.Log("[UIAutoBuilder] MainScreen wired with overlays (no camera target)");
            }
        }
    }
}
