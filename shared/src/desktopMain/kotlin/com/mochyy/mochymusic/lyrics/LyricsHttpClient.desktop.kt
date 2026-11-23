package com.mochyy.mochymusic.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// the desktop app has the same OkHttp engine dependency Android has
private val client: HttpClient by lazy {
    HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }
}

internal actual fun lyricsHttpClient(): HttpClient = client
