// CloudHttp.android.kt

package com.mochyy.mochymusic.backup

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// OkHttp; a backup can be big and the line slow, so reads wait up to two minutes like before
actual fun cloudHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 120_000
    }
}
