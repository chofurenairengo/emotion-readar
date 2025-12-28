using System;
using System.Collections;
using System.Text;
using UnityEngine;
using UnityEngine.Networking;

/// <summary>
/// Gemini API から「返答候補2つ」をJSONで取得する（Unity側）
/// - Schema強制は使わず、プロンプト+堅牢なJSON抽出/検証で安定化
/// - parts結合 / JSON( object or array )抽出 / 2段階リトライ
/// </summary>
public class TestChat : MonoBehaviour
{
    [Header("Gemini API (AI Studio)")]
    [SerializeField] private string apiKey = "";          // Inspectorで入れる
    [SerializeField] private string model = "gemini-2.5-flash";

    [Header("Behavior")]
    [SerializeField] private float timeoutSeconds = 20f;
    [SerializeField] private bool logRawResponse = false;

    [TextArea(2, 8)]
    [SerializeField]
    private string systemInstruction =
        "あなたは会話補助AIです。ユーザーが相手に返しやすい短い返答候補を2つ作ってください。" +
        "各候補は日本語で1文、30文字以内。解説や理由は禁止。";

    // ===== Gemini response (minimal) =====
    [Serializable]
    private class GeminiResponse
    {
        public Candidate[] candidates;

        [Serializable]
        public class Candidate
        {
            public Content content;
        }

        [Serializable]
        public class Content
        {
            public Part[] parts;
        }

        [Serializable]
        public class Part
        {
            public string text;
        }
    }

    // 期待JSON: {"choices":["...","..."]}
    [Serializable]
    private class ChoicesPayload
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

        yield return SendOnce(partnerUtterance, onDone, attempt: 1);
    }

    /// <summary>
    /// attempt: 1 or 2（2回目は追撃プロンプトで強制力を上げる）
    /// </summary>
    private IEnumerator SendOnce(string partnerUtterance, Action<string[], string> onDone, int attempt)
    {
        bool doneCalled = false;

        void Done(string[] choices, string err)
        {
            if (doneCalled) return;
            doneCalled = true;
            onDone?.Invoke(choices, err);
        }

        string url = $"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}";

        // ---- Prompt ----
        // 1回目: 通常制約
        // 2回目: JSON以外出したら失敗、必ずchoicesを2要素、など強く縛る
        string prompt = BuildPrompt(partnerUtterance, attempt);

        string requestJson = BuildRequestJson(prompt);

        using var req = new UnityWebRequest(url, "POST");
        req.uploadHandler = new UploadHandlerRaw(Encoding.UTF8.GetBytes(requestJson));
        req.downloadHandler = new DownloadHandlerBuffer();
        req.SetRequestHeader("Content-Type", "application/json");
        req.timeout = Mathf.CeilToInt(timeoutSeconds);

        yield return req.SendWebRequest();

        if (req.result != UnityWebRequest.Result.Success)
        {
            Done(null, $"HTTP Error: {req.responseCode} {req.error}\n{req.downloadHandler.text}");
            yield break;
        }

        string raw = req.downloadHandler.text;
        if (logRawResponse) Debug.Log("Gemini Raw:\n" + raw);

        // 1) Geminiレスポンス(JSON)からモデル出力テキスト抽出（parts結合・候補探索）
        string modelText = ExtractModelTextRobust(raw, out string extractErr);
        if (modelText == null)
        {
            if (attempt < 2)
            {
                yield return SendOnce(partnerUtterance, onDone, attempt: attempt + 1);
                yield break;
            }

            Done(null, "Gemini response parse failed: " + extractErr + "\n" + raw);
            yield break;
        }

        // 2) JSON値（object/array）抽出（```json 等、前後の文章が混ざってもOK）
        string jsonValue = ExtractFirstJsonValue(modelText);

        if (string.IsNullOrEmpty(jsonValue))
        {
            if (attempt < 2)
            {
                yield return SendOnce(partnerUtterance, onDone, attempt: attempt + 1);
                yield break;
            }

            Done(null, "Model did not return valid JSON (even after retry).\nReturned:\n" + modelText);
            yield break;
        }

        // 3) 期待形式にパースして検証
        ChoicesPayload payload = null;
        string parseErr = null;

        try
        {
            payload = JsonUtility.FromJson<ChoicesPayload>(jsonValue);
        }
        catch (Exception e)
        {
            parseErr = e.Message;
        }

        if (!IsValidChoices(payload, out string validateErr))
        {
            // JSONが配列で返ってくるタイプもあるので救済： ["a","b"] の場合
            // ExtractFirstJsonValue が array を返す可能性があるのでここで対応
            if (TryParseAsStringArray(jsonValue, out var arr) && arr.Length == 2)
            {
                // 文字数制約など軽く整形
                arr[0] = (arr[0] ?? "").Trim();
                arr[1] = (arr[1] ?? "").Trim();
                Done(arr, null);
                yield break;
            }

            if (attempt < 2)
            {
                yield return SendOnce(partnerUtterance, onDone, attempt: attempt + 1);
                yield break;
            }

            Done(null,
                "JSON parsed but choices were invalid (even after retry).\n" +
                (parseErr != null ? "ParseError: " + parseErr + "\n" : "") +
                "ValidateError: " + validateErr + "\n" +
                "JSON:\n" + jsonValue + "\n\nReturned:\n" + modelText);

            yield break;
        }

        // 正常：2つ返す
        payload.choices[0] = (payload.choices[0] ?? "").Trim();
        payload.choices[1] = (payload.choices[1] ?? "").Trim();
        Done(payload.choices, null);
    }

    // ===== Prompt Builder =====
    private string BuildPrompt(string partnerUtterance, int attempt)
    {
        var sb = new StringBuilder();
        sb.AppendLine(systemInstruction);
        sb.AppendLine();
        sb.AppendLine($"相手の発話:「{partnerUtterance}」");
        sb.AppendLine();
        sb.AppendLine("出力は必ずJSONのみ。余計な文章、```、前置きは禁止。");

        if (attempt >= 2)
        {
            sb.AppendLine("JSONは次の形を厳守:");
            sb.AppendLine("{\"choices\":[\"候補1\",\"候補2\"]}");
            sb.AppendLine("choicesは必ず2要素。各要素は日本語1文、30文字以内。");
        }
        else
        {
            sb.AppendLine("JSON形式: {\"choices\":[\"候補1\",\"候補2\"]}");
            sb.AppendLine("choicesは2要素。");
        }

        return sb.ToString();
    }

    // ===== Request JSON Builder =====
    // JsonUtilityは使わず手で作る（Gemini request構造が入れ子で面倒なため）
    private static string BuildRequestJson(string prompt)
    {
        string escPrompt = JsonEscape(prompt);

        var sb = new StringBuilder();
        sb.Append("{");
        sb.Append("\"contents\":[{");
        sb.Append("\"role\":\"user\",");
        sb.Append("\"parts\":[{\"text\":\"").Append(escPrompt).Append("\"}]");
        sb.Append("}],");
        sb.Append("\"generationConfig\":{");
        sb.Append("\"temperature\":0.2,");
        sb.Append("\"maxOutputTokens\":512,"); // 256→512
        sb.Append("\"responseMimeType\":\"application/json\""); // ★追加
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

    /// <summary>
    /// candidates/parts をなるべく頑丈に結合してテキストを取り出す
    /// </summary>
    private static string ExtractModelTextRobust(string raw, out string error)
    {
        error = null;
        try
        {
            var res = JsonUtility.FromJson<GeminiResponse>(raw);
            if (res?.candidates == null || res.candidates.Length == 0)
            {
                error = "candidates was empty.";
                return null;
            }

            // candidatesを順に見て、partsのtextを全部結合する
            for (int c = 0; c < res.candidates.Length; c++)
            {
                var parts = res.candidates[c]?.content?.parts;
                if (parts == null || parts.Length == 0) continue;

                var sb = new StringBuilder();
                for (int i = 0; i < parts.Length; i++)
                {
                    if (!string.IsNullOrEmpty(parts[i]?.text))
                        sb.Append(parts[i].text);
                }

                var text = sb.ToString().Trim();
                if (!string.IsNullOrEmpty(text))
                    return text;
            }

            error = "Model text was empty in all candidates/parts.";
            return null;
        }
        catch (Exception e)
        {
            error = e.Message;
            return null;
        }
    }

    /// <summary>
    /// 文字列中から最初のJSON値（object または array）を抽出する
    /// - ```json/``` を除去
    /// - 先頭の余計な文章があってもOK
    /// </summary>
    private static string ExtractFirstJsonValue(string s)
    {
        if (string.IsNullOrEmpty(s)) return null;

        s = s.Replace("```json", "").Replace("```", "").Trim();

        int objStart = s.IndexOf('{');
        int arrStart = s.IndexOf('[');

        int start;
        char open, close;
        if (objStart < 0 && arrStart < 0) return null;
        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart))
        {
            start = objStart; open = '{'; close = '}';
        }
        else
        {
            start = arrStart; open = '['; close = ']';
        }

        int depth = 0;
        bool inString = false;
        bool escape = false;

        for (int i = start; i < s.Length; i++)
        {
            char ch = s[i];

            if (inString)
            {
                if (escape) { escape = false; continue; }
                if (ch == '\\') { escape = true; continue; }
                if (ch == '"') { inString = false; continue; }
                continue;
            }

            if (ch == '"') { inString = true; continue; }

            if (ch == open) depth++;
            else if (ch == close)
            {
                depth--;
                if (depth == 0)
                {
                    return s.Substring(start, i - start + 1).Trim();
                }
            }
        }

        return null; // 閉じが見つからなかった
    }

    private static bool IsValidChoices(ChoicesPayload payload, out string error)
    {
        error = null;
        if (payload == null) { error = "payload is null"; return false; }
        if (payload.choices == null) { error = "choices is null"; return false; }
        if (payload.choices.Length != 2) { error = $"choices length must be 2 (actual {payload.choices.Length})"; return false; }

        for (int i = 0; i < 2; i++)
        {
            var t = (payload.choices[i] ?? "").Trim();
            if (t.Length == 0) { error = $"choice[{i}] was empty"; return false; }
            // 30文字以内（必要なら厳密に）
            if (t.Length > 30) { error = $"choice[{i}] exceeds 30 chars: {t.Length}"; return false; }
        }

        return true;
    }

    /// <summary>
    /// JsonUtilityは string[] のトップレベル配列を直接パースできないので救済用
    /// ["a","b"] を {"items":["a","b"]} に包んでパースする
    /// </summary>
    private static bool TryParseAsStringArray(string jsonValue, out string[] arr)
    {
        arr = null;
        if (string.IsNullOrEmpty(jsonValue)) return false;
        jsonValue = jsonValue.Trim();
        if (!jsonValue.StartsWith("[") || !jsonValue.EndsWith("]")) return false;

        try
        {
            var wrapped = "{\"items\":" + jsonValue + "}";
            var tmp = JsonUtility.FromJson<StringArrayWrapper>(wrapped);
            if (tmp?.items == null) return false;
            arr = tmp.items;
            return true;
        }
        catch
        {
            return false;
        }
    }

    [Serializable]
    private class StringArrayWrapper
    {
        public string[] items;
    }
}
