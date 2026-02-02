package com.commuxr.android

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.commuxr.android.core.ui.component.RequirePermission
import com.commuxr.android.core.ui.theme.AndroidTheme
import com.commuxr.android.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        setContent {
            AndroidTheme {
                RequirePermission(
                    permission = android.Manifest.permission.CAMERA,
                    rationaleMessage = "カメラを使用して表情を解析します"
                ) {
                    MainScreen()
                }
            }
        }
    }
}
