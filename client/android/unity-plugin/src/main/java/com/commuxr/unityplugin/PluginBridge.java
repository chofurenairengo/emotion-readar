package com.commuxr.unityplugin;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

public final class PluginBridge {
    private static final String TAG = "PluginBridge";
    private static FaceAnalyzer faceAnalyzer;

    private PluginBridge() {}

    public static void start() {
        Log.d(TAG, "start()");

        try {
            Class<?> unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
            Activity activity = (Activity) unityPlayerClass.getField("currentActivity").get(null);

            // ★ UnityのActivityがLifecycleOwnerでなくても、ProcessLifecycleOwnerを使えば解決できる
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (faceAnalyzer != null) {
                        faceAnalyzer.stop();
                    }

                    // アプリ全体のライフサイクルを取得
                    LifecycleOwner lifecycleOwner = ProcessLifecycleOwner.get();
                    
                    faceAnalyzer = new FaceAnalyzer(activity, lifecycleOwner, (json) -> {
                        sendUnityMessage("FaceReceiver", "OnFaceData", json);
                        return null; 
                    });
                    
                    faceAnalyzer.start();
                    Log.d(TAG, "FaceAnalyzer started successfully with ProcessLifecycleOwner");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start FaceAnalyzer in main thread", e);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize PluginBridge", e);
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
