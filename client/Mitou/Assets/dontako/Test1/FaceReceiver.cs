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
