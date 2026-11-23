// LyricsTranslationServices.kt
//
// The translation back ends the lyrics translate button calls (DeepL, Mistral, OpenRouter and its
// streaming variant). Their requests live in :app's api/ next to the rest of the app's network
// services, so shared code asks through this and the Android app implements it over those.

package com.mochyy.mochymusic.lyrics

import kotlinx.coroutines.flow.Flow

/** One piece of a streaming translation: text as it arrives, the finished lines, or an error. */
sealed interface LyricsTranslationChunk {
    data class Content(val text: String) : LyricsTranslationChunk
    data class Complete(val translatedLines: List<String>) : LyricsTranslationChunk
    data class Error(val message: String) : LyricsTranslationChunk
}

interface LyricsTranslationServices {
    suspend fun deepl(
        text: String,
        targetLanguage: String,
        apiKey: String,
        formality: String,
    ): Result<List<String>>

    suspend fun mistral(
        text: String,
        targetLanguage: String,
        apiKey: String,
        model: String,
        mode: String,
    ): Result<List<String>>

    suspend fun openRouter(
        text: String,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
    ): Result<List<String>>

    fun openRouterStreaming(
        text: String,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
    ): Flow<LyricsTranslationChunk>
}
