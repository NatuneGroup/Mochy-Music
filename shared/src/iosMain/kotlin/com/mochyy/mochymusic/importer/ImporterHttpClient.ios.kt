// ImporterHttpClient.ios.kt

package com.mochyy.mochymusic.importer

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

// NSURLSession through Ktor's Darwin engine, with the same waits as the other two
internal actual fun createImporterHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    expectSuccess = false
}
