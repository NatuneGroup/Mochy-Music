// DiscordHttpClient.ios.kt

package com.mochyy.mochymusic.discord

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets

// NSURLSession through Ktor's Darwin engine, which carries WebSockets too on iOS 13 and later
actual fun discordHttpClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 20_000
        socketTimeoutMillis = 20_000
    }
    install(WebSockets)
}
