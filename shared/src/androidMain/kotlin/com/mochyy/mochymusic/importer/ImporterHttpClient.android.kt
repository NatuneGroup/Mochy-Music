// ImporterHttpClient.android.kt

package com.mochyy.mochymusic.importer

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// OkHttp, the engine the app already ships
internal actual fun createImporterHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    expectSuccess = false
}
