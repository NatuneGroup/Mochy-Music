package com.mochyy.mochymusic.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// the same client the app's Google Translate engine and shared lyrics providers built
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
