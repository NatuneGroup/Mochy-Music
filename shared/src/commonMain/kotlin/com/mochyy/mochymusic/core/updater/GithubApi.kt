// GithubApi.kt

package com.mochyy.mochymusic.core.updater

import com.mochyy.mochymusic.link.createDeviceConnectHttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

// The one GET the changelog and commit screens make. The shared device client is a plain http
// client on every target, so the update screens can read github without the app at all.
class GithubAnswer(val code: Int, val text: String)

object GithubApi {
    private const val RequestTimeoutMs = 30_000L

    private val client by lazy { createDeviceConnectHttpClient() }

    // null when the call couldn't be made at all; a non-200 answer comes back with its code
    suspend fun get(url: String, accept: String, userAgent: String): GithubAnswer? = runCatching {
        val response = client.get(url) {
            header("Accept", accept)
            header("User-Agent", userAgent)
            timeout { requestTimeoutMillis = RequestTimeoutMs }
        }
        GithubAnswer(response.status.value, response.bodyAsText())
    }.getOrNull()
}
