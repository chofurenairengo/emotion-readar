package com.commuxr.unityplugin;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public final class PluginBridge {
    private static final String TAG = "PluginBridge";

    private PluginBridge() {}

    public static void start() {
        Log.d(TAG, "start()");

        // 実験2用テストJSON
        String json =
                "{"
                        + "\"type\":\"face\","
                        + "\"timestampMs\":123456789,"
                        + "\"blendshapes\":[{\"name\":\"mouthSmileLeft\",\"score\":0.42}],"
                        + "\"landmarks\":[[0.5,0.5,-0.01],[0.52,0.48,-0.02]]"
                        + "}";

        Log.d(TAG, "about to send Unity message");

        // ★ メインスレッドで UnitySendMessage
        new Handler(Looper.getMainLooper()).post(() -> {
            sendUnityMessage("FaceReceiver", "OnFaceData", json);
        });
    }

    public static void stop() {
        Log.d(TAG, "stop()");
    }

    private static void sendUnityMessage(String gameObject, String method, String message) {
        try {
            Class<?> unityPlayer = Class.forName("com.unity3d.player.UnityPlayer");
            java.lang.reflect.Method unitySendMessage = unityPlayer.getMethod(
                    "UnitySendMessage",
                    String.class,
                    String.class,
                    String.class
            );
            unitySendMessage.invoke(null, gameObject, method, message);
            Log.d(TAG, "UnitySendMessage SUCCESS");
        } catch (Throwable error) {
            Log.e(TAG, "UnitySendMessage FAILED", error);
        }
    }
}
