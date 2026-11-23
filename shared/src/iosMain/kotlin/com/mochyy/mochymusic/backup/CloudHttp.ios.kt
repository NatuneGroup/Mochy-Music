// CloudHttp.ios.kt

package com.mochyy.mochymusic.backup

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

// NSURLSession through Ktor's Darwin engine, with the same waits
actual fun cloudHttpClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 120_000
    }
}
