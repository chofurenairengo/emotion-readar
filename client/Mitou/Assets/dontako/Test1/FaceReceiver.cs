using System.Collections;
using System;
using System.Collections.Generic;
using System.Globalization;
using UnityEngine;

public class FaceReceiver : MonoBehaviour {
    public string lastJson;
    public bool DebugJson = false;
    public float smile = -1f;
    public void OnLog(string message) {
        Debug.Log(message);
    }
    
    public void OnFaceData(string json) {
        lastJson = json;
        if (DebugJson) {
            Debug.Log("[FaceReceiver] OnFaceData: " + json);
        }

        // 文字列から "smile": の後の数字を直接抜く
        // smile に加えて以下を追加
smile = ExtractLiteValue(json, "\"smile\":", smile);
float gazeX = ExtractLiteValue(json, "\"gazeX\":", 0f);
float gazeY = ExtractLiteValue(json, "\"gazeY\":", 0f);
float yaw   = ExtractLiteValue(json, "\"yaw\":", 0f);


    }

    [Serializable]
    public class Blendshape {
        public string name;
        public float score;
    }

    [Serializable]
    public class FacePayload {
        public string type;
        public long timestampMs;
        public Blendshape[] blendshapes;

        // landmarksは実験2では未使用
        // public float[][] landmarks;
    }

        private float ExtractLiteValue(string json, string key, float fallback) {
        int index = json.IndexOf(key);
        if (index < 0) return fallback;
        int start = index + key.Length;
        int end = json.IndexOfAny(new char[] { ',', '}', ']' }, start);
        string val = json.Substring(start, (end < 0 ? json.Length : end) - start);
        if (float.TryParse(val, NumberStyles.Float, CultureInfo.InvariantCulture, out float res)) return res;
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
