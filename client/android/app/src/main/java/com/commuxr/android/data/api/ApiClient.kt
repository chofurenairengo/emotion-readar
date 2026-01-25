package com.commuxr.android.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API クライアントの設定と提供を行うシングルトンオブジェクト
 */
object ApiClient {
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"  // Android Emulator localhost
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L

    private var baseUrl: String = DEFAULT_BASE_URL

    /**
     * Moshi インスタンス（JSON シリアライゼーション）
     */
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * HTTPロギングインターセプター（デバッグ用）
     */
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (com.commuxr.android.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    /**
     * OkHttp クライアント
     */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrofit インスタンス
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Comm-XR API インスタンス
     */
    val api: CommXRApi by lazy {
        retrofit.create(CommXRApi::class.java)
    }

    /**
     * テストやデバッグ用にベースURLを変更
     *
     * 注意: アプリ起動前に呼び出すこと（lazy初期化前）
     * @param url 新しいベースURL
     */
    fun setBaseUrl(url: String) {
        baseUrl = url
    }
}
