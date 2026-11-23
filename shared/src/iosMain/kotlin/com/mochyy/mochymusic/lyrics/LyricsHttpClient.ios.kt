package com.mochyy.mochymusic.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

private val client: HttpClient by lazy {
    HttpClient(Darwin) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }
}

internal actual fun lyricsHttpClient(): HttpClient = client
