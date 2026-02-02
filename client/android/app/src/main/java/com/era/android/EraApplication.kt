package com.era.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * ERAアプリケーションクラス
 *
 * Hiltによる依存性注入を有効化
 */
@HiltAndroidApp
class EraApplication : Application()
