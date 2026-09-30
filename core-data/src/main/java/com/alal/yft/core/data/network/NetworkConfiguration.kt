package com.alal.yft.core.data.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object NetworkConfiguration {
    fun createClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
