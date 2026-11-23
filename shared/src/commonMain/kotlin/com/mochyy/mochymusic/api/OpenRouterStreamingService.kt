// OpenRouterStreamingService.kt

package com.mochyy.mochymusic.api

import com.mochyy.mochymusic.utils.ioDispatcher
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object OpenRouterStreamingService {
    fun streamTranslation(
        text: String,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
    ): Flow<StreamChunk> = flow {
        if (text.isBlank()) {
            emit(StreamChunk.Error("Input text is empty"))
            return@flow
        }

        val lines = text.lines()
        val lineCount = lines.size

        println("OpenRouter: starting streaming translation for $lineCount lines")

        try {
            val systemPrompt = AiPrompts.system(lineCount)
            val userPrompt = AiPrompts.streamingUser(mode, targetLanguage, lineCount, text)

            val messages = buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    },
                )
            }

            val jsonBody = buildJsonObject {
                if (model.isNotBlank()) {
                    put("model", model)
                }
                put("messages", messages)
                put("stream", true)
                put("temperature", 0.3)
                put("max_tokens", lineCount * 100)
            }

            aiHttpClient().preparePost(baseUrl.ifBlank { "https://openrouter.ai/api/v1/chat/completions" }) {
                contentType(ContentType.Application.Json)
                if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}")
                header("HTTP-Referer", AiHttp.OpenRouterReferer)
                header("X-Title", "mochy")
                setBody(jsonBody.toString())
            }.execute { response ->
                println("OpenRouter: got streaming response ${response.status.value}")

                if (!response.status.isSuccess()) {
                    var errorBody = ""
                    val channel = response.bodyAsChannel()
                    while (true) {
                        val line = channel.readUTF8Line() ?: break
                        errorBody += line + "\n"
                    }
                    val errorMsg = AiJson.objectOf(errorBody)?.get("error").obj()?.get("message").string()
                        ?: "HTTP ${response.status.value}: ${response.status.description}"
                    emit(StreamChunk.Error("Translation failed: $errorMsg"))
                    return@execute
                }

                val channel = response.bodyAsChannel()
                val contentBuilder = StringBuilder()
                var chunkCount = 0
                var done = false

                while (!done) {
                    val currentLine = channel.readUTF8Line() ?: break
                    if (!currentLine.startsWith("data: ")) continue
                    val data = currentLine.substring(6)
                    if (data == "[DONE]") {
                        println("OpenRouter: streaming complete, received $chunkCount chunks")
                        val result = parseTranslationContent(contentBuilder.toString(), lineCount)
                        result.onSuccess { emit(StreamChunk.Complete(it)) }
                            .onFailure { emit(StreamChunk.Error(it.message ?: "Parsing failed")) }
                        done = true
                        break
                    }

                    try {
                        val jsonObject = AiJson.json.parseToJsonElement(data).obj()
                        val content = jsonObject?.get("choices").array()?.firstOrNull()
                            ?.obj()?.get("delta").obj()?.get("content").string()
                        if (content != null) {
                            contentBuilder.append(content)
                            chunkCount++
                            emit(StreamChunk.Content(content))
                        }
                    } catch (e: Exception) {
                        // a malformed chunk is skipped, as it always was
                        println("OpenRouter: ignored malformed chunk (${e.message})")
                    }
                }

                if (!done && contentBuilder.isNotEmpty()) {
                    println("OpenRouter: stream ended without [DONE], parsing what came")
                    val result = parseTranslationContent(contentBuilder.toString(), lineCount)
                    result.onSuccess { emit(StreamChunk.Complete(it)) }
                        .onFailure { emit(StreamChunk.Error(it.message ?: "Parsing failed")) }
                }
            }
        } catch (e: Exception) {
            println("OpenRouter: streaming error (${e.message})")
            emit(StreamChunk.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(ioDispatcher)

    sealed class StreamChunk {
        data class Content(val text: String) : StreamChunk()
        data class Complete(val translatedLines: List<String>) : StreamChunk()
        data class Error(val message: String) : StreamChunk()
    }
}
