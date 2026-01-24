using System;
using System.Collections.Generic;
using System.Globalization;
using UnityEngine;
using UnityEngine.UI; // ★テキスト表示のために追加

public class FaceReceiver : MonoBehaviour {
    [Header("UI Reference")]
    public Text statusText; // ★UnityのInspectorでTextコンポーネントをアタッチしてください

    [Header("Real-time Data")]
    public float smile = 0f;
    public float gazeX = 0f;
    public float gazeY = 0f;
    public float yaw = 0f;
    public long timestamp;

    [Header("Debug")]
    public bool debugJson = false;
    public string lastJson;

    public void OnFaceData(string json) {
        lastJson = json;
        if (debugJson) Debug.Log("[FaceReceiver] " + json);

        // 各値を抽出してメンバ変数に保存
        smile = ExtractLiteValue(json, "\"smile\":", smile);
        gazeX = ExtractLiteValue(json, "\"gazeX\":", gazeX);
        gazeY = ExtractLiteValue(json, "\"gazeY\":", gazeY);
        yaw   = ExtractLiteValue(json, "\"yaw\":", yaw);

        // リアルタイムでテキストを更新
        UpdateStatusText();
    }

    private void UpdateStatusText() {
        if (statusText == null) return;

        statusText.text = $"[Face Data]\n" +
                          $"Smile: {smile:F2}\n" +
                          $"Gaze X: {gazeX:F2}\n" +
                          $"Gaze Y: {gazeY:F2}\n" +
                          $"Head Yaw: {yaw:F2}\n" +
                          $"Updated: {DateTime.Now:HH:mm:ss}";
    }

    private float ExtractLiteValue(string json, string key, float fallback) {
        try {
            int index = json.IndexOf(key);
            if (index < 0) return fallback;
            int start = index + key.Length;
            int end = json.IndexOfAny(new char[] { ',', '}', ']' }, start);
            string val = json.Substring(start, (end < 0 ? json.Length : end) - start);
            if (float.TryParse(val, NumberStyles.Float, CultureInfo.InvariantCulture, out float res)) {
                return res;
            }
        } catch {}
        return fallback;
    }
}
    /*
    UnitySendMessage(
        "FaceReceiver",   // GameObject名
        "OnFaceData",     // メソッド名
        jsonString        // 引数（string 1個）
    );
    Kotlin側がこんな感じで１回呼ぶと、
    Unity側のFaceReceiverクラスのOnFaceDataメソッドが呼ばれる。
    GameObject名が完全一致じゃないといけないのもポイント。
    */

    /*
    public void OnFaceData(string json)
    {
        Debug.Log($"[OnFaceData] raw: {json}");

        try
        {
            var data = JsonUtility.FromJson<FacePayload>(json);

            float mouthSmileLeft = -1f;
            if (data.blendshapes != null)
            {
                foreach (var b in data.blendshapes)
                {
                    if (b != null && b.name == "mouthSmileLeft")
                    {
                        mouthSmileLeft = b.score;
                        break;
                    }
                }
            }

            Debug.Log($"type={data.type}, ts={data.timestampMs}, mouthSmileLeft={mouthSmileLeft}");
        }
        catch (Exception e)
        {
            Debug.LogError($"Json parse failed: {e.Message}\n{e.StackTrace}");
        }
    }
    */
    /*
    private static float ExtractBlendshapeScore(string json, string targetName, float fallback) {
        if (string.IsNullOrEmpty(json) || string.IsNullOrEmpty(targetName)) return fallback;

        // "name":"mouthSmileLeft" を探す
        string key = "\"name\":\"" + targetName + "\"";
        int nameIndex = json.IndexOf(key, StringComparison.Ordinal);
        if (nameIndex < 0) return fallback;

        // nameの近くにある "score": を探す
        int scoreKeyIndex = json.IndexOf("\"score\":", nameIndex, StringComparison.Ordinal);
        if (scoreKeyIndex < 0) return fallback;

        int valueStart = scoreKeyIndex + "\"score\":".Length;

        // 数値の終端（, or } or ]）
        int valueEnd = valueStart;
        while (valueEnd < json.Length)
        {
            char c = json[valueEnd];
            if (c == ',' || c == '}' || c == ']' || c == '\n' || c == '\r' || c == ' ')
                break;
            valueEnd++;
        }

        if (valueEnd <= valueStart) return fallback;

        string number = json.Substring(valueStart, valueEnd - valueStart);

        // ★ InvariantCulture で安全にパース
        if (float.TryParse(number, NumberStyles.Float, CultureInfo.InvariantCulture, out float v))
        {
            return Mathf.Clamp01(v);
        }

        return fallback;
    }
    */
