// OpenRouterService.kt

package com.mochyy.mochymusic.api

import com.mochyy.mochymusic.utils.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object OpenRouterService {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        maxRetries: Int = 3,
        sourceLanguage: String? = null,
    ): Result<List<String>> = withContext(ioDispatcher) {
        var currentAttempt = 0

        if (text.isBlank()) {
            return@withContext Result.failure(Exception("Input text is empty"))
        }

        val lines = text.lines()
        val lineCount = lines.size

        while (currentAttempt < maxRetries) {
            try {
                val systemPrompt = AiPrompts.system(lineCount)
                val userPrompt = AiPrompts.user(mode, targetLanguage, lineCount, text)

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
                    put("temperature", 0.3)
                    put("max_tokens", lineCount * 100)
                }

                val reply = AiHttp.postJson(
                    url = baseUrl.ifBlank { "https://openrouter.ai/api/v1/chat/completions" },
                    body = jsonBody.toString(),
                    headers = buildMap {
                        if (apiKey.isNotBlank()) put("Authorization", "Bearer ${apiKey.trim()}")
                        put("HTTP-Referer", AiHttp.OpenRouterReferer)
                        put("X-Title", "mochy")
                    },
                )

                if (!reply.ok) {
                    if (reply.code >= 500) {
                        currentAttempt++
                        delay(1000L * currentAttempt)
                        continue
                    }

                    return@withContext Result.failure(
                        Exception("Translation failed: ${AiHttp.errorMessage(reply.body, reply)}"),
                    )
                }

                val content = AiJson.objectOf(reply.body)?.get("choices").array()?.firstOrNull()
                    ?.obj()?.get("message").string()?.trim()

                if (!content.isNullOrBlank()) {
                    parseTranslationContent(content, lineCount).getOrNull()
                        ?.let { return@withContext Result.success(it) }
                }
            } catch (e: Exception) {
                if (currentAttempt == maxRetries - 1) {
                    return@withContext Result.failure(e)
                }
            }
            currentAttempt++
            delay(1000L * currentAttempt)
        }
        return@withContext Result.failure(Exception("Max retries exceeded"))
    }
}
