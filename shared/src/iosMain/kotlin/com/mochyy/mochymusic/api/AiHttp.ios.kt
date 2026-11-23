// AiHttp.ios.kt

package com.mochyy.mochymusic.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

// NSURLSession through Ktor's Darwin engine, with the same waits
actual fun aiHttpClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 90_000
    }
}
