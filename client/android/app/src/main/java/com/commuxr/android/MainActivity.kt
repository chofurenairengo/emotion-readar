package com.commuxr.android

import android.content.pm.ActivityInfo
import android.os.Bundle
import com.unity3d.player.UnityPlayerActivity

/**
 * メインActivity
 * UnityPlayerActivityを継承してUnity描画エンジンを起動する。
 */
class MainActivity : UnityPlayerActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}
