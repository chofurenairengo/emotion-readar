using System.Collections;
using System;
using System.Collections.Generic;
using UnityEngine;

public class FaceReceiver : MonoBehaviour {
    /*
    public void OnLog(string message) {
        Debug.Log(message);
    }
    */

    [Serializable]
    public class Blendshape
    {
        public string name;
        public float score;
    }

    [Serializable]
    public class FacePayload
    {
        public string type;
        public long timestampMs;
        public Blendshape[] blendshapes;

        // landmarksは実験2では未使用（JsonUtilityで多次元配列は厳しい）
        // public float[][] landmarks; ← これは後で別方式にする
    }

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
}