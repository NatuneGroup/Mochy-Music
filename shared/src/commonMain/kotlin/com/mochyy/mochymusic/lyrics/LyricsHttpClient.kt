// LyricsHttpClient.kt
//
// One HTTP client for the shared lyrics code that answers without a host-supplied one: Google's
// translate endpoint and the shared lyrics sources. Each platform picks its own engine and the
// same timeouts the app has always used; providers never build an engine of their own.

package com.mochyy.mochymusic.lyrics

import io.ktor.client.HttpClient

internal expect fun lyricsHttpClient(): HttpClient
