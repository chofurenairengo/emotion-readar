package com.commuxr.unityplugin

object PayloadFactory {
    @JvmStatic
    fun sampleJson(): String =
        """
        {
          "type": "face",
          "timestampMs": 123456789,
          "blendshapes": [
            { "name": "mouthSmileLeft", "score": 0.42 }
          ],
          "landmarks": [
            [0.5, 0.5, -0.01],
            [0.52, 0.48, -0.02]
          ]
        }
        """.trimIndent()
}
