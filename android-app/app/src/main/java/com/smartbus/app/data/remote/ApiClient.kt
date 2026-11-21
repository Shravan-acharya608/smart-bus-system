package com.smartbus.app.data.remote

import com.smartbus.app.BuildConfig
import com.smartbus.app.data.local.UserPreferences
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(private val userPreferences: UserPreferences) {

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = runBlocking { userPreferences.accessTokenFlow.firstOrNull() }

        val requestBuilder = original.newBuilder()
        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val fallbackInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        try {
            chain.proceed(originalRequest)
        } catch (e: Exception) {
            val isNetworkError = e is java.net.ConnectException || e is java.net.SocketTimeoutException
            val host = originalRequest.url.host
            if (isNetworkError && (host == "10.236.83.220" || host == "192.168.43.196" || host == "127.0.0.1" || host == "10.0.2.2")) {
                val candidateHosts = listOf("10.236.83.220", "127.0.0.1", "10.0.2.2", "192.168.43.196").filter { it != host }
                var lastException = e
                for (fallbackHost in candidateHosts) {
                    try {
                        val newUrl = originalRequest.url.newBuilder().host(fallbackHost).build()
                        val newRequest = originalRequest.newBuilder().url(newUrl).build()
                        return@Interceptor chain.proceed(newRequest)
                    } catch (retryEx: Exception) {
                        lastException = retryEx
                    }
                }
                throw lastException
            } else {
                throw e
            }
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(fallbackInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun createService(baseUrl: String = BuildConfig.BASE_URL): ApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
