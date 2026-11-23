// AiHttp.kt
//
// One client and one JSON reader for the AI translation services (OpenRouter, Mistral, DeepL and
// the streaming OpenRouter call). Each platform installs its engine; the waits are the ones the
// OkHttp clients had (30 s connect, 90 s request/write; the streaming call gets 120 s).

package com.mochyy.mochymusic.api

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

expect fun aiHttpClient(): HttpClient

/** what a request answered: the status, its line and the whole body, read once. */
internal class AiReply(val code: Int, val body: String, val statusLine: String = "") {
    val ok: Boolean get() = code in 200..299
}

internal object AiJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun objectOf(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull

    fun JsonElement?.array(): JsonArray? = this as? JsonArray

    fun JsonElement?.obj(): JsonObject? = this as? JsonObject
}

internal object AiHttp {
    const val OpenRouterReferer = "https://github.com/realidkroo/Mochy-Music"

    suspend fun postJson(url: String, body: String, headers: Map<String, String>): AiReply {
        val response = aiHttpClient().post(url) {
            contentType(ContentType.Application.Json)
            headers.forEach { (name, value) -> header(name, value) }
            setBody(body)
        }
        return AiReply(response.status.value, response.bodyAsText(), response.status.description)
    }

    /** the `error.message` these APIs send, or their status line, the way the OkHttp code read it */
    fun errorMessage(body: String, reply: AiReply): String {
        val message = AiJson.objectOf(body)?.get("error").obj()?.get("message").string()
        return message ?: "HTTP ${reply.code}: ${reply.statusLine}"
    }
}

/** Parses a model's answer into exactly [expectedLineCount] lines, or fails. */
internal fun parseTranslationContent(content: String, expectedLineCount: Int): Result<List<String>> {
    var translatedLines: List<String>? = null

    runCatching {
        translatedLines = AiJson.json.parseToJsonElement(content.trim()).jsonArray.map { it.jsonPrimitive.contentOrNull.orEmpty() }
    }.onFailure {
        val cleanedContent = content.replace("```json", "").replace("```", "").trim()
        runCatching {
            translatedLines = AiJson.json.parseToJsonElement(cleanedContent).jsonArray.map { it.jsonPrimitive.contentOrNull.orEmpty() }
        }.onFailure {
            val startIdx = cleanedContent.indexOf('[')
            val endIdx = cleanedContent.lastIndexOf(']')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val jsonString = cleanedContent.substring(startIdx, endIdx + 1)
                runCatching {
                    translatedLines = AiJson.json.parseToJsonElement(jsonString).jsonArray.map { it.jsonPrimitive.contentOrNull.orEmpty() }
                }.onFailure {
                    translatedLines = cleanedContent.lines()
                        .filter { it.trim().isNotEmpty() }
                        .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                }
            }
        }
    }

    val lines = translatedLines ?: return Result.failure(Exception("Failed to parse translation"))
    return when {
        lines.size == expectedLineCount -> Result.success(lines)
        lines.size > expectedLineCount -> Result.success(lines.take(expectedLineCount))
        else -> Result.success(lines + List(expectedLineCount - lines.size) { "" })
    }
}
