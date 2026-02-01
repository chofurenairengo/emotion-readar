package com.commuxr.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Comm-XRアプリケーションクラス
 *
 * Hiltによる依存性注入を有効化
 */
@HiltAndroidApp
class CommuxrApplication : Application()
