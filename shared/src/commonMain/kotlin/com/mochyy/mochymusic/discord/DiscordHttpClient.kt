// DiscordHttpClient.kt
//
// The client every Discord call and the gateway's WebSocket go through. It used to be one OkHttp
// client; each platform now installs its own engine here, with the same 10 s connect and 20 s
// read/write waits, and the WebSockets plugin so the gateway can open its socket on the same client.

package com.mochyy.mochymusic.discord

import io.ktor.client.HttpClient

expect fun discordHttpClient(): HttpClient
