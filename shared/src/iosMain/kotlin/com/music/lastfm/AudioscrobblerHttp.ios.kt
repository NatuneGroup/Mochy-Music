// AudioscrobblerHttp.ios.kt

package com.music.lastfm

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

// NSURLSession through Ktor's Darwin engine, the same waits Android uses
actual fun audioscrobblerHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    expectSuccess = false
}
