// AudioscrobblerHttp.android.kt

package com.music.lastfm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// OkHttp, the engine Android already ships; the same waits the scrobbling client always had
actual fun audioscrobblerHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    expectSuccess = false
}
