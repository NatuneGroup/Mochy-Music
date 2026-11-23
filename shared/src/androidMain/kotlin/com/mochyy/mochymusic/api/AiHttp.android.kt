// AiHttp.android.kt

package com.mochyy.mochymusic.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// OkHttp with the waits the app's own AI client had: 30 s to connect, 90 s between reads
actual fun aiHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 90_000
    }
}
