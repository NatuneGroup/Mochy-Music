// AiHttp.desktop.kt

package com.mochyy.mochymusic.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// the desktop installs the same OkHttp engine Android does
actual fun aiHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 90_000
    }
}
