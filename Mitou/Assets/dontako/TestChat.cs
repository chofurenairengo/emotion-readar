using System;
using System.Collections;
using System.Text;
using UnityEngine;
using UnityEngine.Networking;

public class TestChat : MonoBehaviour
{
    [Header("Gemini API (AI Studio)")]
    [SerializeField] private string apiKey = ""; // Inspectorで入れる
    [SerializeField] private string model = "gemini-2.5-flash";

    [TextArea(2, 8)]
    [SerializeField] private string systemInstruction =
        "あなたは会話補助AIです。ユーザーが相手に返しやすい短い返答候補を2つ作ってください。" +
        "各候補は日本語で1文、30文字以内。解説や理由は禁止。";

    // Gemini response（最低限）
    [Serializable] private class GeminiResponse
    {
        public Candidate[] candidates;

        [Serializable] public class Candidate
        {
            public Content content;
        }

        [Serializable] public class Content
        {
            public Part[] parts;
        }

        [Serializable] public class Part
        {
            public string text;
        }
    }

    [Serializable] private class ChoicesPayload
    {
        public string[] choices;
    }

    public void RequestChoices(string partnerUtterance, Action<string[], string> onDone)
    {
        StartCoroutine(RequestChoicesCoroutine(partnerUtterance, onDone));
    }

    private IEnumerator RequestChoicesCoroutine(string partnerUtterance, Action<string[], string> onDone)
    {
        if (string.IsNullOrWhiteSpace(apiKey))
        {
            onDone?.Invoke(null, "API key is empty. InspectorからapiKeyを入れてください。");
            yield break;
        }

        partnerUtterance = (partnerUtterance ?? "").Trim();
        if (string.IsNullOrWhiteSpace(partnerUtterance))
        {
            onDone?.Invoke(null, "Input is empty.");
            yield break;
        }

        yield return SendOnce(partnerUtterance, onDone, isRetry: false);
    }

    private IEnumerator SendOnce(string partnerUtterance, Action<string[], string> onDone, bool isRetry)
    {
        string url = $"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}";

        // prompt
        string prompt =
            $"{systemInstruction}\n\n" +
            $"相手の発話:「{partnerUtterance}」\n" +
            "返答候補を2つ。";

        // ★ JsonUtilityを使わず、RESTに送るJSONを手で作る
        // ★ responseMimeType + responseSchema で JSONを強制
        string requestJson = BuildRequestJson(prompt);

        using var req = new UnityWebRequest(url, "POST");
        req.uploadHandler = new UploadHandlerRaw(Encoding.UTF8.GetBytes(requestJson));
        req.downloadHandler = new DownloadHandlerBuffer();
        req.SetRequestHeader("Content-Type", "application/json");

        yield return req.SendWebRequest();

        if (req.result != UnityWebRequest.Result.Success)
        {
            onDone?.Invoke(null, $"HTTP Error: {req.responseCode} {req.error}\n{req.downloadHandler.text}");
            yield break;
        }

        // 1) Geminiレスポンス(JSON)を読む
        string raw = req.downloadHandler.text;

        string modelText = ExtractModelText(raw, out string extractErr);
        if (modelText == null)
        {
            onDone?.Invoke(null, "Gemini response parse failed: " + extractErr + "\n" + raw);
            yield break;
        }

        // Debug.Log("ModelText:\n" + modelText);

        // 2) 念のため ```json を剥がして JSON部分抜く
        string jsonOnly = ExtractFirstJsonObject(modelText);
        if (string.IsNullOrEmpty(jsonOnly))
        {
            if (!isRetry)
            {
                // 1回だけリトライ
                yield return SendOnce(partnerUtterance, onDone, isRetry: true);
                yield break;
            }
            onDone?.Invoke(null, "Model did not return valid JSON (even after retry).\nReturned:\n" + modelText);
            yield break;
        }

        // 3) JSONパース（try/catch内でyieldしない）
        ChoicesPayload payload = null;
        string parseErr = null;

        try
        {
            payload = JsonUtility.FromJson<ChoicesPayload>(jsonOnly);
        }
        catch (Exception e)
        {
            parseErr = e.Message;
        }

        bool ok = payload != null && payload.choices != null && payload.choices.Length == 2;
        if (!ok)
        {
            if (!isRetry)
            {
                yield return SendOnce(partnerUtterance, onDone, isRetry: true);
                yield break;
            }

            onDone?.Invoke(null,
                "JSON parsed but choices were invalid (even after retry).\n" +
                (parseErr != null ? "ParseError: " + parseErr + "\n" : "") +
                "JSON:\n" + jsonOnly + "\n\nReturned:\n" + modelText);
            yield break;
        }

        onDone?.Invoke(payload.choices, null);
    }

    // ===== JSON Builder =====
    private static string BuildRequestJson(string prompt)
    {
        // JSONエスケープ（最低限）
        string escPrompt = JsonEscape(prompt);

        // ★ responseSchema は protobuf enum形式で type を送る（OBJECT/ARRAY/STRING）
        // ★ choices は必ず2要素
        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"contents\":[{");
        sb.Append("\"role\":\"user\",");
        sb.Append("\"parts\":[{\"text\":\"").Append(escPrompt).Append("\"}]");
        sb.Append("}],");

        sb.Append("\"generationConfig\":{");
        sb.Append("\"temperature\":0.2,");
        sb.Append("\"maxOutputTokens\":128,");
        sb.Append("\"responseMimeType\":\"application/json\",");
        sb.Append("\"responseSchema\":{");
        sb.Append("\"type\":\"OBJECT\",");
        sb.Append("\"required\":[\"choices\"],");
        sb.Append("\"properties\":{");
        sb.Append("\"choices\":{");
        sb.Append("\"type\":\"ARRAY\",");
        sb.Append("\"minItems\":2,");
        sb.Append("\"maxItems\":2,");
        sb.Append("\"items\":{");
        sb.Append("\"type\":\"STRING\"");
        sb.Append("}");
        sb.Append("}");
        sb.Append("}");
        sb.Append("}");
        sb.Append("}");
        sb.Append("}");

        return sb.ToString();
    }

    private static string JsonEscape(string s)
    {
        if (s == null) return "";
        return s
            .Replace("\\", "\\\\")
            .Replace("\"", "\\\"")
            .Replace("\n", "\\n")
            .Replace("\r", "\\r")
            .Replace("\t", "\\t");
    }

    // ===== Response helpers =====
    private static string ExtractModelText(string raw, out string error)
    {
        error = null;
        try
        {
            var res = JsonUtility.FromJson<GeminiResponse>(raw);
            var text = res?.candidates?[0]?.content?.parts?[0]?.text;
            if (string.IsNullOrEmpty(text))
            {
                error = "Model text was empty.";
                return null;
            }
            return text;
        }
        catch (Exception e)
        {
            error = e.Message;
            return null;
        }
    }

    private static string ExtractFirstJsonObject(string s)
    {
        if (string.IsNullOrEmpty(s)) return null;

        s = s.Replace("```json", "").Replace("```", "").Trim();

        int start = s.IndexOf('{');
        if (start < 0) return null;

        int depth = 0;
        for (int i = start; i < s.Length; i++)
        {
            if (s[i] == '{') depth++;
            else if (s[i] == '}')
            {
                depth--;
                if (depth == 0)
                    return s.Substring(start, i - start + 1);
            }
        }
        return null; // 途中で切れてたらnull
    }
}
