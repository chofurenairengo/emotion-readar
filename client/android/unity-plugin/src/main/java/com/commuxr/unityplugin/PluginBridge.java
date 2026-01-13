package com.commuxr.unityplugin;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.lifecycle.LifecycleOwner;

public final class PluginBridge {
    private static final String TAG = "PluginBridge";
    private static FaceAnalyzer faceAnalyzer;

    private PluginBridge() {}

    public static void start() {
        Log.d(TAG, "start()");

        try {
            // UnityのPlayerActivityを取得
            Class<?> unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
            Activity activity = (Activity) unityPlayerClass.getField("currentActivity").get(null);

            if (activity instanceof LifecycleOwner) {
                final LifecycleOwner lifecycleOwner = (LifecycleOwner) activity;
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (faceAnalyzer != null) {
                        faceAnalyzer.stop();
                    }
                    faceAnalyzer = new FaceAnalyzer(activity, lifecycleOwner, (json) -> {
                        sendUnityMessage("FaceReceiver", "OnFaceData", json);
                        return null; // Unit in Kotlin
                    });
                    faceAnalyzer.start();
                    Log.d(TAG, "FaceAnalyzer started");
                });
            } else {
                Log.e(TAG, "Activity is not a LifecycleOwner. Make sure you are using a compatible Unity version or custom Activity.");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize FaceAnalyzer", e);
        }
    }

    public static void stop() {
        Log.d(TAG, "stop()");
        if (faceAnalyzer != null) {
            faceAnalyzer.stop();
            faceAnalyzer = null;
        }
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
            Log.d(TAG, "UnitySendMessage SUCCESS: " + message);
        } catch (Throwable error) {
            Log.e(TAG, "UnitySendMessage FAILED", error);
        }
    }
}
