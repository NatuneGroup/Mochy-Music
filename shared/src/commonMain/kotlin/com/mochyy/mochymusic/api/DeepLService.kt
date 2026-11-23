// DeepLService.kt

package com.mochyy.mochymusic.api

import com.mochyy.mochymusic.utils.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object DeepLService {
    suspend fun translate(
        text: String,
        targetLanguage: String,
        apiKey: String,
        formality: String = "default",
        maxRetries: Int = 3,
    ): Result<List<String>> = withContext(ioDispatcher) {
        var currentAttempt = 0

        if (text.isBlank()) {
            return@withContext Result.failure(Exception("Input text is empty"))
        }

        val lines = text.lines()
        val lineCount = lines.size

        val deeplLangCode = when (targetLanguage.lowercase()) {
            "zh", "zh-cn", "zh-hans" -> "ZH"
            "zh-tw", "zh-hant" -> "ZH"
            "en", "en-us" -> "EN-US"
            "en-gb" -> "EN-GB"
            "pt", "pt-pt" -> "PT-PT"
            "pt-br" -> "PT-BR"
            else -> targetLanguage.uppercase().take(2)
        }

        val baseUrl = if (apiKey.endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }

        while (currentAttempt < maxRetries) {
            try {
                val jsonBody = buildJsonObject {
                    put("text", buildJsonArray { lines.forEach { add(it) } })
                    put("target_lang", deeplLangCode)
                    if (formality != "default") {
                        put("formality", formality)
                    }
                    put("preserve_formatting", true)
                }

                val reply = AiHttp.postJson(
                    url = baseUrl,
                    body = jsonBody.toString(),
                    headers = mapOf("Authorization" to "DeepL-Auth-Key ${apiKey.trim()}"),
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

                val translations = AiJson.objectOf(reply.body)?.get("translations").array()
                if (translations != null && translations.isNotEmpty()) {
                    val translatedLines = translations.map { entry ->
                        entry.obj()?.get("text").string().orEmpty()
                    }

                    return@withContext when {
                        translatedLines.size == lineCount -> Result.success(translatedLines)
                        translatedLines.size > lineCount -> Result.success(translatedLines.take(lineCount))
                        else -> Result.success(translatedLines + List(lineCount - translatedLines.size) { "" })
                    }
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
