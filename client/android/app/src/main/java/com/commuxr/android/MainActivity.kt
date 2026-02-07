package com.commuxr.android

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.commuxr.android.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * メインActivity
 * アプリ起動時にViewModelを通じてセッションが自動開始される。
 * Unity統合時にUnityPlayerActivityを継承に変更する。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}
