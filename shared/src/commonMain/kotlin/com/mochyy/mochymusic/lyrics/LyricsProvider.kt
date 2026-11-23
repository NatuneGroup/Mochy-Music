// LyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import com.mochyy.mochymusic.constants.PreferredLyricsProvider

/**
 * One lyrics source, as shared code sees it: a name and a lookup. Whether the source is switched
 * on, and whether the device is online, belong to the orchestrator ([LyricsHelper]), which reads
 * them from the one settings store and the platform's connectivity — a provider only answers.
 */
interface LyricsProvider {
    val name: String

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String>

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration, album).onSuccess(callback)
    }
}

/**
 * The sources the app has, in the order it offers them. Android's LyricsProviderRegistry supplies
 * the instances (they wrap Android-only provider modules and the module system); the ordering rules
 * themselves are shared, [LyricsSourceOrder], so every platform sorts the same way.
 */
interface LyricsProviderCatalog {
    /** Modules that are on, then every built-in source. */
    val providerNames: List<String>

    fun getProviderByName(name: String): LyricsProvider?

    fun getDefaultProviderOrder(): List<String>

    fun deserializeProviderOrder(orderString: String): List<String>

    fun effectiveProviderOrder(orderString: String, preferred: PreferredLyricsProvider): List<String>
}
