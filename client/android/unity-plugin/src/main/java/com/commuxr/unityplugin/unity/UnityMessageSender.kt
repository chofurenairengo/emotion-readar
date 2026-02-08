package com.commuxr.unityplugin.unity

import android.os.Handler
import android.os.Looper
import android.util.Log

class UnityMessageSender(
    private val gameObjectName: String,
    private val methodName: String,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun send(message: String) {
        mainHandler.post {
            try {
                val unityPlayer = Class.forName("com.unity3d.player.UnityPlayer")
                val unitySendMessage = unityPlayer.getMethod(
                    "UnitySendMessage",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                )
                unitySendMessage.invoke(null, gameObjectName, methodName, message)
            } catch (error: Throwable) {
                Log.e(TAG, "UnitySendMessage failed", error)
            }
        }
    }

    companion object {
        private const val TAG = "UnityMessageSender"
    }
}
