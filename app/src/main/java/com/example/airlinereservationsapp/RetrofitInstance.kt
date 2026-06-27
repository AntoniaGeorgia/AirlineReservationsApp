package com.example.airlinereservationsapp

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Build
import android.os.Looper
import androidx.core.content.edit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.util.Log

object RetrofitInstance {
    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86")

    private val BASE_URL = if (isEmulator()) {
        "http://10.0.2.2:8081/"
    } else {
        "http://192.168.1.131:8081/"}.also { Log.d("RetrofitInstance", "BASE_URL = $it") }

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val api: ApiService by lazy {
        val authInterceptor = Interceptor { chain ->
            val token = appContext
                .getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getString("jwt_token", null)

            Log.d("RetrofitInstance", "Tokenul meu JWT este: $token")

            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val sessionExpiredInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val path = chain.request().url.encodedPath
            if (response.code == 401 && path != "/login" && path != "/register") {
                handleSessionExpired()
            }
            response
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(sessionExpiredInterceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun handleSessionExpired() {
        appContext.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit { clear() }
        Handler(Looper.getMainLooper()).post {
            appContext.startActivity(
                Intent(appContext, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("SESSION_EXPIRED", true)
                }
            )
        }
    }
}