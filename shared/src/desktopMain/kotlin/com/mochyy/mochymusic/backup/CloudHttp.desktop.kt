// CloudHttp.desktop.kt

package com.mochyy.mochymusic.backup

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// the desktop installs the same OkHttp engine Android does
actual fun cloudHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 120_000
    }
}
