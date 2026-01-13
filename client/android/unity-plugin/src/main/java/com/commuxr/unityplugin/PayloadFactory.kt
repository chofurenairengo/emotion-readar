package com.commuxr.unityplugin

import com.google.gson.JsonObject

object PayloadFactory {
    /**
     * フェーズ2拡張: 笑顔、視線、頭部の向きを含む軽量JSONを生成
     */
    fun createFaceLite(
        smile: Float,
        eyeGazeX: Float, // -1.0 (左) ~ 1.0 (右)
        eyeGazeY: Float, // -1.0 (下) ~ 1.0 (上)
        headPitch: Float, // 上下回転
        headYaw: Float,   // 左右回転
        headRoll: Float   // 傾き
    ): String {
        val root = JsonObject()
        root.addProperty("type", "face-lite")
        root.addProperty("timestampMs", System.currentTimeMillis())
        
        val metrics = JsonObject()
        metrics.addProperty("smile", smile)
        metrics.addProperty("gazeX", eyeGazeX)
        metrics.addProperty("gazeY", eyeGazeY)
        metrics.addProperty("pitch", headPitch)
        metrics.addProperty("yaw", headYaw)
        metrics.addProperty("roll", headRoll)
        
        root.add("metrics", metrics)
        return root.toString()
    }

    @JvmStatic
    fun sampleJson(): String =
        """
        {
          "type": "face",
          "timestampMs": 123456789,
          "metrics": {
             "smile": 0.42,
             "gazeX": 0.1,
             "gazeY": -0.05,
             "pitch": 0.0,
             "yaw": 0.0,
             "roll": 0.0
          }
        }
        """.trimIndent()
}
