// PictureHosts.kt
//
// Discord only shows pictures that live on the web, so the ones made or kept on the phone go to a
// free file host first. The uploads were OkHttp's multipart; they are Ktor's now, so every
// platform can make them.

package com.mochyy.mochymusic.discord

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.mochyy.mochymusic.db.nowEpochMillis

object PictureHosts {
    // where a picture went, and when to stop using it, a little before the host deletes it
    class Hosted(val url: String, val usableUntilMs: Long)

    private class Host(val name: String, val keepsMs: Long, val upload: suspend (bytes: ByteArray, fileName: String, mime: String) -> String)

    private const val HourMs = 60 * 60_000L
    private val json = Json { ignoreUnknownKeys = true }

    // checked october 2026 by uploading a gif and fetching it back as discord's crawler: uguu serves the file itself, qu.ax and tmpfiles serve a web page
    private val Hosts = listOf(
        Host("uguu.se", keepsMs = 3 * HourMs) { bytes, name, mime ->
            val reply = post("https://uguu.se/upload", "files[]", bytes, name, mime)
            json.parseToJsonElement(reply).jsonObject["files"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
        },
        // catbox keeps files for good; the 30 days only bound how long mochy reuses one
        Host("catbox.moe", keepsMs = 30 * 24 * HourMs) { bytes, name, mime ->
            post("https://catbox.moe/user/api.php", "fileToUpload", bytes, name, mime, "reqtype" to "fileupload")
        },
        Host("litterbox.catbox.moe", keepsMs = 72 * HourMs) { bytes, name, mime ->
            post(
                "https://litterbox.catbox.moe/resources/internals/api.php", "fileToUpload", bytes, name, mime,
                "reqtype" to "fileupload", "time" to "72h",
            )
        },
    )

    // tries each host in turn: free hosts come and go, so one being down mustn't stop the picture
    suspend fun upload(bytes: ByteArray, fileName: String, mime: String): Hosted {
        var lastProblem: Exception? = null
        for (host in Hosts) {
            try {
                val url = host.upload(bytes, fileName, mime)
                check(url.startsWith("https://")) { "${host.name} sent back \"${url.take(120)}\"" }
                return Hosted(url, nowEpochMillis() + host.keepsMs * 9 / 10)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("${host.name} didn't take $fileName (${e.message})")
                lastProblem = e
            }
        }
        throw lastProblem ?: IllegalStateException("no host to upload to")
    }

    // sends [bytes] as a form upload and returns the reply's text, trimmed
    private suspend fun post(url: String, fileField: String, bytes: ByteArray, fileName: String, mime: String, vararg fields: Pair<String, String>): String {
        val response = DiscordHttp.client.post(url) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        fields.forEach { (name, value) -> append(name, value) }
                        append(
                            fileField,
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }
        val text = response.bodyAsText().trim()
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        return text
    }

    private fun log(message: String) = println("PictureHosts: $message")
}
