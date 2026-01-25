using System.Collections;
using System;
using System.Collections.Generic;
using UnityEngine;
using NativeWebSocket;
//
using Newtonsoft.Json;

public class FaceWebSocketReceiver : MonoBehaviour
{   
    [SerializeField] private string serverUrl = "ws://127.0.0.1:8765";
    [SerializeField] private bool autoConnectOnStart = true;

    [SerializeField] private bool logRawJson = false;
    [SerializeField] private bool logSummary = true;

    private WebSocket _ws;

    // Unity内の他スクリプトが参照できる「最新フレーム」
    public FaceFrame LatestFrame { get; private set; }

    public event Action<FaceFrame> OnFaceFrame;

    // 受信スレッド→メインスレッドに渡すためのキュー
    private readonly Queue<string> _mainThreadQueue = new Queue<string>();

    private async void Start()
    {
        if (autoConnectOnStart)
        {
            await Connect();
        }
    }

    public async System.Threading.Tasks.Task Connect()
    {
        await Disconnect();

        _ws = new WebSocket(serverUrl);

        _ws.OnOpen += () =>
        {
            Debug.Log($"[FaceWS] Connected: {serverUrl}");
        };

        _ws.OnError += (e) =>
        {
            Debug.LogError($"[FaceWS] Error: {e}");
        };

        _ws.OnClose += (e) =>
        {
            Debug.Log($"[FaceWS] Closed: code={e}");
        };

        _ws.OnMessage += (bytes) =>
        {
            var json = System.Text.Encoding.UTF8.GetString(bytes);
            lock (_mainThreadQueue)
            {
                _mainThreadQueue.Enqueue(json);
            }
        };

        try
        {
            await _ws.Connect();
        }
        catch (Exception ex)
        {
            Debug.LogError($"[FaceWS] Connect exception: {ex.Message}");
        }
    }

    public async System.Threading.Tasks.Task Disconnect()
    {
        if (_ws == null) return;

        try
        {
            await _ws.Close();
        }
        catch { /* ignore */ }

        _ws = null;
    }

    private void Update()
    {
        // NativeWebSocketはこれを呼ばないとEditor/Standaloneで受信が回らない
#if !UNITY_WEBGL || UNITY_EDITOR
        _ws?.DispatchMessageQueue();
#endif

        // キューをメインスレッドで処理
        while (true)
        {
            string json = null;
            lock (_mainThreadQueue)
            {
                if (_mainThreadQueue.Count > 0)
                    json = _mainThreadQueue.Dequeue();
            }

            if (json == null) break;

            HandleJson(json);
        }
    }

    private void HandleJson(string json)
    {
        if (logRawJson) Debug.Log($"[FaceWS] RAW: {json}");

        FaceMessage msg;
        try
        {
            msg = JsonConvert.DeserializeObject<FaceMessage>(json);
        }
        catch (Exception ex)
        {
            Debug.LogError($"[FaceWS] JSON parse failed: {ex.Message}\n{json}");
            return;
        }

        if (msg == null || msg.Type != "face")
        {
            // 今回はtype=face以外は無視（将来hands/pose追加してもOK）
            return;
        }

        // Blendshape: name -> score
        var map = new Dictionary<string, float>(capacity: msg.Blendshapes?.Count ?? 0);
        if (msg.Blendshapes != null)
        {
            foreach (var b in msg.Blendshapes)
            {
                if (b == null || string.IsNullOrEmpty(b.Name)) continue;
                map[b.Name] = b.Score;
            }
        }

        // Landmarks: [[x,y,z], ...] -> Vector3[]
        Vector3[] landmarks = Array.Empty<Vector3>();
        if (msg.Landmarks != null && msg.Landmarks.Count > 0)
        {
            landmarks = new Vector3[msg.Landmarks.Count];
            for (int i = 0; i < msg.Landmarks.Count; i++)
            {
                var v = msg.Landmarks[i];
                if (v == null || v.Count < 3)
                {
                    landmarks[i] = Vector3.zero;
                    continue;
                }
                landmarks[i] = new Vector3(v[0], v[1], v[2]);
            }
        }

        var frame = new FaceFrame(msg.TimestampMs, map, landmarks);
        LatestFrame = frame;
        OnFaceFrame?.Invoke(frame);

        if (logSummary)
        {
            int bsCount = frame.BlendshapeMap?.Count ?? 0;
            int lmCount = frame.Landmarks?.Length ?? 0;

            float smileL = 0f;

            // 例：mouthSmileLeft が来てるか見る（無くても0になるだけ）
            frame.BlendshapeMap?.TryGetValue("mouthSmileLeft", out smileL);

            Debug.Log($"[FaceWS] face ts={frame.TimestampMs} blendshapes={bsCount} landmarks={lmCount} mouthSmileLeft={smileL:0.000}");
        }
    }

    private async void OnApplicationQuit()
    {
        await Disconnect();
    }
}
