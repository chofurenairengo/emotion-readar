package com.era.android.di

import com.era.android.data.repository.SessionRepository
import com.era.android.data.websocket.WebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * アプリケーションレベルの依存性注入モジュール
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSessionRepository(): SessionRepository {
        return SessionRepository()
    }

    @Provides
    @Singleton
    fun provideWebSocketClient(): WebSocketClient {
        return WebSocketClient()
    }
}
