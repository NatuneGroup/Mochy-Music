// AndroidLyricsTranslationServices.kt
//
// The translation back ends in api/ as one LyricsTranslationServices, so the shared
// LyricsTranslationHelper can drive them. Same requests, same results; only the interface is new.

package com.mochyy.mochymusic.lyrics

import com.mochyy.mochymusic.api.DeepLService
import com.mochyy.mochymusic.api.MistralService
import com.mochyy.mochymusic.api.OpenRouterService
import com.mochyy.mochymusic.api.OpenRouterStreamingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object AndroidLyricsTranslationServices : LyricsTranslationServices {
    override suspend fun deepl(
        text: String,
        targetLanguage: String,
        apiKey: String,
        formality: String,
    ): Result<List<String>> = DeepLService.translate(
        text = text,
        targetLanguage = targetLanguage,
        apiKey = apiKey,
        formality = formality,
    )

    override suspend fun mistral(
        text: String,
        targetLanguage: String,
        apiKey: String,
        model: String,
        mode: String,
    ): Result<List<String>> = MistralService.translate(
        text = text,
        targetLanguage = targetLanguage,
        apiKey = apiKey,
        model = model,
        mode = mode,
    )

    override suspend fun openRouter(
        text: String,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
    ): Result<List<String>> = OpenRouterService.translate(
        text = text,
        targetLanguage = targetLanguage,
        apiKey = apiKey,
        baseUrl = baseUrl,
        model = model,
        mode = mode,
    )

    override fun openRouterStreaming(
        text: String,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
    ): Flow<LyricsTranslationChunk> = OpenRouterStreamingService.streamTranslation(
        text = text,
        targetLanguage = targetLanguage,
        apiKey = apiKey,
        baseUrl = baseUrl,
        model = model,
        mode = mode,
    ).map { chunk ->
        when (chunk) {
            is OpenRouterStreamingService.StreamChunk.Content -> LyricsTranslationChunk.Content(chunk.text)
            is OpenRouterStreamingService.StreamChunk.Complete -> LyricsTranslationChunk.Complete(chunk.translatedLines)
            is OpenRouterStreamingService.StreamChunk.Error -> LyricsTranslationChunk.Error(chunk.message)
        }
    }
}
