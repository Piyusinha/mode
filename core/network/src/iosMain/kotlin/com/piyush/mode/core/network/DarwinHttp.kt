package com.piyush.mode.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/** The app's HTTP client on iOS (URLSession underneath), with a timeout long enough for model calls. */
public fun darwinHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureRequest { setTimeoutInterval(90.0) }
    }
}
