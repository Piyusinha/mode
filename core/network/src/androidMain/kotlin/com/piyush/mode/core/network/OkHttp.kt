package com.piyush.mode.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

/** The app's HTTP client on Android (OkHttp underneath), with a timeout long enough for model calls. */
public fun okHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            writeTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        }
    }
}

private const val TIMEOUT_S = 90L
