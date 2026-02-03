package com.commuxr.android

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * メインActivity
 * UIはUnity側で表示するため、このActivityは空。
 * Unity統合時にUnityPlayerActivityを継承に変更する。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // UIはUnity側で表示
    }
}
