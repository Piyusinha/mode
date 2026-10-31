package com.piyush.mode.agent

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/** The HTTP client for model calls on iOS (URLSession underneath). */
fun darwinHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureRequest { setTimeoutInterval(90.0) }
    }
}
