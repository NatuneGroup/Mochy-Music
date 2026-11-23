// LyricsTranslationHelper.kt

package com.mochyy.mochymusic.lyrics

import com.mochyy.mochymusic.db.MochyDatabase
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.ai_error_api_key_required
import com.mochyy.mochymusic.shared.resources.ai_error_language_required
import com.mochyy.mochymusic.shared.resources.ai_error_lyrics_empty
import com.mochyy.mochymusic.shared.resources.ai_error_no_lyrics
import com.mochyy.mochymusic.shared.resources.ai_error_translation_failed
import com.mochyy.mochymusic.shared.resources.ai_error_unknown
import com.mochyy.mochymusic.shared.resources.ai_error_unexpected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

object LyricsTranslationHelper {
    // the platform's translation back ends; the Android app installs the ones in api/ once
    @Volatile private var services: LyricsTranslationServices? = null
    @Volatile private var captions: LyricsCaptionTranslation? = null

    /** Called by the host once; every later translate uses these back ends. */
    fun install(translationServices: LyricsTranslationServices, captionTranslation: LyricsCaptionTranslation) {
        services = translationServices
        captions = captionTranslation
    }

    // the bridged string when the resources are readable, the same English text otherwise: an error
    // path must never fail into a crash just because the message could not be loaded
    private suspend fun message(resource: StringResource, fallback: String): String =
        try {
            getString(resource)
        } catch (e: Exception) {
            fallback
        }

    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    private val _translationSaved = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val translationSaved: SharedFlow<Unit> = _translationSaved.asSharedFlow()

    private var translationJob: Job? = null
    private var isCompositionActive = true

    private val translationCache = LyricsLru<String, List<String>>(64)

    private fun getCacheKey(lyricsText: String, mode: String, language: String): String =
        "${lyricsText.hashCode()}_${mode}_$language"

    private fun tryParsePartialTranslation(content: String, expectedCount: Int): List<String> {
        val startIdx = content.indexOf('[')
        if (startIdx == -1) return emptyList()

        val result = mutableListOf<String>()
        var pos = startIdx + 1
        var inString = false
        var escaping = false
        val currentString = StringBuilder()

        while (pos < content.length && result.size < expectedCount) {
            val char = content[pos]

            when {
                escaping -> {
                    currentString.append(char)
                    escaping = false
                }
                char == '\\' && inString -> {
                    currentString.append(char)
                    escaping = true
                }
                char == '"' -> {
                    if (inString) {
                        result.add(currentString.toString())
                        currentString.clear()
                        inString = false
                    } else {
                        inString = true
                    }
                }
                inString -> {
                    currentString.append(char)
                }
                char == ']' -> {
                    break
                }
            }
            pos++
        }

        return result
    }

    fun getCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): List<String>? {
        val lyricsText = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        val key = getCacheKey(lyricsText, mode, language)
        return lyricsLocked(translationCache) { translationCache.get(key) }
    }

    fun applyCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): Boolean {
        val cached = getCachedTranslations(lyrics, mode, language) ?: return false
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        if (cached.size >= nonEmptyEntries.size) {
            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                lyrics[originalIndex].translatedTextFlow.value = cached[idx]
            }
            return true
        }
        return false
    }

    /**
     * Whether translations should be on screen. This is the toggle the translate button flips.
     *
     * It used to be implicit: "on" meant "some line currently has a translation", and "off" only
     * blanked the lines. Nothing remembered the choice, so the stored translation reappeared on
     * the next database emission, a new song never translated, and changing the language had
     * nothing to re-run against.
     */
    private val _translationOn = MutableStateFlow(false)
    val translationOn: StateFlow<Boolean> = _translationOn.asStateFlow()

    private var lastSongId: String? = null

    /**
     * What a stored translation is filed under. Just the target when the source is detected
     * (as before, so existing stored translations still match), "source>target" when the user
     * picked the source, so picking one after a bad detection really translates again instead of
     * reloading the stored copy.
     */
    fun languageTag(sourceLanguage: String, targetLanguage: String): String =
        if (sourceLanguage.isBlank() || sourceLanguage == "auto") targetLanguage else "$sourceLanguage>$targetLanguage"

    fun triggerManualTranslation() {
        _translationOn.value = true
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        _translationOn.value = false
        _hasActiveTranslations.value = false
        translationJob?.cancel()
        _status.value = TranslationStatus.Idle
        _clearTranslationsTrigger.tryEmit(Unit)
    }

    /**
     * Called when the playing song changes. Translation carries over to the next song only when
     * "translate this whole session" is on; otherwise each song starts untranslated.
     */
    fun onSongChanged(songId: String?, sessionWide: Boolean) {
        if (songId == null) return
        if (lastSongId != null && lastSongId != songId && !sessionWide) {
            _translationOn.value = false
            _hasActiveTranslations.value = false
        }
        lastSongId = songId
    }

    fun hasTranslations(lyricsEntity: LyricsEntity?): Boolean = !lyricsEntity?.translatedLyrics.isNullOrBlank()

    fun clearTranslations(lyricsEntity: LyricsEntity): LyricsEntity =
        lyricsEntity.copy(
            translatedLyrics = "",
            translationLanguage = "",
            translationMode = "",
        )

    fun resetStatus() {
        _status.value = TranslationStatus.Idle
    }

    fun clearCache() {
        lyricsLocked(translationCache) { translationCache.evictAll() }
    }

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
    }

    fun cancelTranslation() {
        isCompositionActive = false
        translationJob?.cancel()
        translationJob = null
    }

    /** @return true when a stored translation matching [targetLanguage] and [mode] was applied. */
    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        lyricsEntity: LyricsEntity?,
        targetLanguage: String,
        mode: String,
    ): Boolean {

        lyrics.forEach { it.translatedTextFlow.value = null }

        if (lyricsEntity?.translatedLyrics.isNullOrBlank()) {
            _hasActiveTranslations.value = false
            return false
        }
        if (lyricsEntity.translationLanguage != targetLanguage) {
            _hasActiveTranslations.value = false
            return false
        }
        if (lyricsEntity.translationMode != mode) {
            _hasActiveTranslations.value = false
            return false
        }

        val translatedLines = lyricsEntity.translatedLyrics.lines()
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
            if (idx < translatedLines.size) {
                // a youtube translation leaves some lines without one, and a blank one would show as an empty row
                lyrics[originalIndex].translatedTextFlow.value = translatedLines[idx].takeIf { it.isNotBlank() }
            }
        }

        val lyricsText = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        val cacheKey = getCacheKey(lyricsText, mode, targetLanguage)
        lyricsLocked(translationCache) { translationCache.put(cacheKey, translatedLines) }
        _hasActiveTranslations.value = true
        return true
    }

    fun translateLyrics(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        scope: CoroutineScope,
        provider: String = "OpenRouter",
        deeplApiKey: String = "",
        deeplFormality: String = "default",
        useStreaming: Boolean = true,
        songId: String = "",
        database: MochyDatabase? = null,
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating

        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob = scope.launchTranslation {
            try {

                val effectiveApiKey = if (provider == "DeepL") deeplApiKey else apiKey
                if (effectiveApiKey.isBlank()) {
                    _status.value = TranslationStatus.Error(message(Res.string.ai_error_api_key_required, "API key is required"))
                    return@launchTranslation
                }

                if (lyrics.isEmpty()) {
                    _status.value = TranslationStatus.Error(message(Res.string.ai_error_no_lyrics, "No lyrics to translate"))
                    return@launchTranslation
                }

                val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
                    if (entry.text.isNotBlank()) index to entry else null
                }

                if (nonEmptyEntries.isEmpty()) {
                    _status.value = TranslationStatus.Error(message(Res.string.ai_error_lyrics_empty, "Lyrics are empty"))
                    return@launchTranslation
                }

                val fullText = nonEmptyEntries.joinToString("\n") { it.second.text }

                val cacheKey = getCacheKey(fullText, mode, targetLanguage)
                val cachedTranslations = lyricsLocked(translationCache) { translationCache.get(cacheKey) }
                if (cachedTranslations != null && cachedTranslations.size >= nonEmptyEntries.size) {

                    nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                        if (idx < cachedTranslations.size) {
                            lyrics[originalIndex].translatedTextFlow.value = cachedTranslations[idx]
                        }
                    }
                    _hasActiveTranslations.value = true
                    _status.value = TranslationStatus.Success

                    if (songId.isNotBlank() && database != null) {
                        try {
                            val currentLyrics = database.songDao.lyrics(songId).first()
                            if (currentLyrics != null && currentLyrics.translatedLyrics.isNullOrBlank()) {
                                database.suspendingDao.upsert(
                                    currentLyrics.copy(
                                        translatedLyrics = cachedTranslations.joinToString("\n"),
                                        translationLanguage = targetLanguage,
                                        translationMode = mode,
                                    ),
                                )
                                _translationSaved.tryEmit(Unit)
                            }
                        } catch (_: Exception) {
                            // the translation is on screen; failing to store it only means it won't show offline
                        }
                    }

                    delay(3000)
                    if (_status.value is TranslationStatus.Success && isCompositionActive) {
                        _status.value = TranslationStatus.Idle
                    }
                    return@launchTranslation
                }

                if (targetLanguage.isBlank()) {
                    _status.value = TranslationStatus.Error(message(Res.string.ai_error_language_required, "Target language is required"))
                    return@launchTranslation
                }

                val fullLanguageName = translationLanguageName(targetLanguage)

                val backEnd = checkNotNull(services) { "Translation services are not installed" }
                val result = if (provider == "DeepL") {
                    backEnd.deepl(
                        text = fullText,
                        targetLanguage = targetLanguage,
                        apiKey = deeplApiKey,
                        formality = deeplFormality,
                    )
                } else if (provider == "Mistral") {
                    backEnd.mistral(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        model = model,
                        mode = mode,
                    )
                } else if (useStreaming && provider != "Custom") {
                    var translatedLines: List<String>? = null
                    var hasError = false
                    var errorMessage = ""
                    val contentAccumulator = StringBuilder()

                    backEnd.openRouterStreaming(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        mode = mode,
                    ).collect { chunk ->
                        when (chunk) {
                            is LyricsTranslationChunk.Content -> {
                                contentAccumulator.append(chunk.text)

                                val partialContent = contentAccumulator.toString()
                                val partialResult = tryParsePartialTranslation(partialContent, nonEmptyEntries.size)
                                if (partialResult.isNotEmpty()) {
                                    partialResult.forEachIndexed { idx, translation ->
                                        if (idx < nonEmptyEntries.size && translation.isNotBlank()) {
                                            val originalIndex = nonEmptyEntries[idx].first
                                            lyrics[originalIndex].translatedTextFlow.value = translation
                                        }
                                    }
                                    _status.value = TranslationStatus.Translating
                                }
                            }
                            is LyricsTranslationChunk.Complete -> {
                                translatedLines = chunk.translatedLines
                            }
                            is LyricsTranslationChunk.Error -> {
                                hasError = true
                                errorMessage = chunk.message
                            }
                        }
                    }

                    if (hasError) {
                        Result.failure(Exception(errorMessage))
                    } else if (translatedLines != null) {
                        Result.success(translatedLines)
                    } else {
                        Result.failure(Exception("No translation received"))
                    }
                } else {
                    backEnd.openRouter(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        mode = mode,
                    )
                }

                result.onSuccess { translatedLines ->
                    if (!isCompositionActive) {
                        return@onSuccess
                    }

                    val cacheKey2 = getCacheKey(fullText, mode, targetLanguage)
                    lyricsLocked(translationCache) { translationCache.put(cacheKey2, translatedLines) }

                    if (songId.isNotBlank() && database != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val currentLyrics = database.songDao.lyrics(songId).first()
                                if (currentLyrics != null) {
                                    database.suspendingDao.upsert(
                                        currentLyrics.copy(
                                            translatedLyrics = translatedLines.joinToString("\n"),
                                            translationLanguage = targetLanguage,
                                            translationMode = mode,
                                        ),
                                    )

                                    _translationSaved.tryEmit(Unit)
                                }
                            } catch (_: Exception) {
                                // stored for offline use only; the lines are already on screen
                            }
                        }
                    }

                    val expectedCount = nonEmptyEntries.size

                    when {
                        translatedLines.size >= expectedCount -> {
                            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                                lyrics[originalIndex].translatedTextFlow.value = translatedLines[idx]
                            }
                            _hasActiveTranslations.value = true
                            _status.value = TranslationStatus.Success
                        }
                        translatedLines.size < expectedCount -> {
                            translatedLines.forEachIndexed { idx, translation ->
                                if (idx < nonEmptyEntries.size) {
                                    val originalIndex = nonEmptyEntries[idx].first
                                    lyrics[originalIndex].translatedTextFlow.value = translation
                                }
                            }
                            _hasActiveTranslations.value = true
                            _status.value = TranslationStatus.Success
                        }
                        else -> {
                            _status.value = TranslationStatus.Error(message(Res.string.ai_error_unexpected, "Unexpected translation result"))
                        }
                    }

                    delay(3000)
                    if (_status.value is TranslationStatus.Success && isCompositionActive) {
                        _status.value = TranslationStatus.Idle
                    }
                }.onFailure { error ->
                    if (!isCompositionActive) {
                        return@onFailure
                    }

                    val errorMessage = error.message ?: message(Res.string.ai_error_unknown, "Unknown error occurred")
                    _status.value = TranslationStatus.Error(errorMessage)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && isCompositionActive) {
                    val errorMessage = e.message ?: message(Res.string.ai_error_translation_failed, "Translation failed")
                    _status.value = TranslationStatus.Error(errorMessage)
                }
            }
        }
    }

    /**
     * Translates via Google Translate, which needs no API key.
     *
     * This exists so the translate toggle does something for a user who has not pasted an
     * OpenRouter or DeepL key into settings — previously that user got silence. Callers should
     * prefer this whenever no AI key is configured.
     */
    fun translateWithGoogle(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        mode: String,
        sourceLanguage: String = "auto",
        scope: CoroutineScope,
        songId: String = "",
        database: MochyDatabase? = null,
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating
        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob = scope.launchTranslation {
            val indexed = lyrics.mapIndexedNotNull { index, entry ->
                if (entry.text.isNotBlank()) index to entry else null
            }
            if (indexed.isEmpty()) {
                _status.value = TranslationStatus.Idle
                return@launchTranslation
            }

            val translated = GoogleTranslateEngine.translate(
                lines = indexed.map { it.second.text },
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage.ifBlank { "auto" },
            )
            if (translated == null) {
                _status.value = TranslationStatus.Error("Translation failed")
                return@launchTranslation
            }

            indexed.forEachIndexed { idx, (originalIndex, entry) ->
                // A line already in the target language comes back unchanged; showing it twice
                // only doubles the line.
                lyrics[originalIndex].translatedTextFlow.value = translated.getOrNull(idx)
                    ?.takeUnless { it.trim().equals(entry.text.trim(), ignoreCase = true) }
            }
            _hasActiveTranslations.value = true
            _status.value = TranslationStatus.Success

            val lyricsText = indexed.joinToString("\n") { it.second.text }
            lyricsLocked(translationCache) {
                translationCache.put(getCacheKey(lyricsText, mode, languageTag(sourceLanguage, targetLanguage)), translated)
            }

            if (songId.isNotBlank() && database != null) {
                try {
                    val current = database.songDao.lyrics(songId).first()
                    if (current != null) {
                        database.suspendingDao.upsert(
                            current.copy(
                                translatedLyrics = translated.joinToString("\n"),
                                translationLanguage = languageTag(sourceLanguage, targetLanguage),
                                translationMode = mode,
                            ),
                        )
                        _translationSaved.tryEmit(Unit)
                    }
                } catch (_: Exception) {
                    // stored for offline use only
                }
            }
        }
    }

    /** shows the video's own uploaded translation when youtube has one in the target language; false when it doesn't, so the caller translates the usual way */
    suspend fun translateWithYouTubeCaptions(
        lyrics: List<LyricsEntry>,
        request: YouTubeCaptionRequest,
        lyricsEntity: LyricsEntity?,
        mode: String,
        database: MochyDatabase,
    ): Boolean {
        val tag = youTubeTranslationLanguageTag(request.targetLanguage)
        // saved from an earlier play, so it shows offline too
        if (loadTranslationsFromDatabase(lyrics, lyricsEntity, tag, mode)) return true

        translationJob?.cancel()
        _status.value = TranslationStatus.Translating
        val translated = try {
            captions?.translate(request)
        } finally {
            if (_status.value is TranslationStatus.Translating) _status.value = TranslationStatus.Idle
        }
        if (translated == null || translated.size != lyrics.size) return false

        lyrics.forEachIndexed { index, entry -> entry.translatedTextFlow.value = translated[index] }
        _hasActiveTranslations.value = true
        _status.value = TranslationStatus.Success

        // stored one row per non-blank line, the same way the other translations are
        val stored = lyrics.indices.filter { lyrics[it].text.isNotBlank() }.joinToString("\n") { translated[it].orEmpty() }
        try {
            val current = database.songDao.lyrics(request.songId).first()
            if (current != null) {
                database.suspendingDao.upsert(current.copy(translatedLyrics = stored, translationLanguage = tag, translationMode = mode))
                _translationSaved.tryEmit(Unit)
            }
        } catch (_: Exception) {
            // stored for offline use only
        }

        delay(3000)
        if (_status.value is TranslationStatus.Success) _status.value = TranslationStatus.Idle
        return true
    }

    /**
     * Launches a translation job that cannot leave the status stuck on Translating. Jobs run in
     * the lyrics screen's scope, so leaving the screen or switching language cancels them; before,
     * a cancelled job left "Translating…" up for good, which disabled the translate button.
     */
    private fun CoroutineScope.launchTranslation(block: suspend CoroutineScope.() -> Unit): Job {
        val job = launch(Dispatchers.IO, block = block)
        job.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException &&
                translationJob === job &&
                _status.value is TranslationStatus.Translating
            ) {
                _status.value = TranslationStatus.Idle
            }
        }
        return job
    }

    sealed class TranslationStatus {
        data object Idle : TranslationStatus()
        data object Translating : TranslationStatus()
        data object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }
}
