// DiscordHttp.kt
//
// Mochy's calls to Discord's web API, and the client the gateway's socket shares. It used to be
// OkHttp; each platform now installs its own engine through `discordHttpClient()`, and the
// gateway gets its WebSocket from the same client, so there is still one connection pool.

package com.mochyy.mochymusic.discord

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.contentType

/** Mochy's calls to Discord's web API and gateway, sharing one connection pool. */
object DiscordHttp {
    const val Api = "https://discord.com/api/v10"

    val client: HttpClient by lazy { discordHttpClient() }

    class Reply(val code: Int, val body: String, val retryAfterSeconds: Double?) {
        val ok: Boolean get() = code in 200..299
    }

    suspend fun get(path: String, token: String): Reply =
        send(path = path, method = HttpMethod.Get, token = token)

    suspend fun postForm(path: String, fields: Map<String, String>): Reply {
        val form = Parameters.build { fields.forEach { (key, value) -> append(key, value) } }
        return send(path = path, method = HttpMethod.Post, form = form)
    }

    suspend fun postJson(path: String, token: String, json: String): Reply =
        send(path = path, method = HttpMethod.Post, token = token, json = json)

    /** Cancelling the coroutine cancels the call too, so leaving the page doesn't leave it hanging. */
    private suspend fun send(
        path: String,
        method: HttpMethod,
        token: String? = null,
        form: Parameters? = null,
        json: String? = null,
    ): Reply {
        val response = client.request(Api + path) {
            this.method = method
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            when {
                form != null -> {
                    setBody(FormDataContent(form))
                }
                json != null -> {
                    contentType(ContentType.Application.Json)
                    setBody(json)
                }
            }
        }
        return Reply(
            code = response.status.value,
            body = response.bodyAsText(),
            retryAfterSeconds = response.headers["Retry-After"]?.toDoubleOrNull(),
        )
    }
}
