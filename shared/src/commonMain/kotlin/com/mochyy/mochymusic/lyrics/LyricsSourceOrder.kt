// LyricsSourceOrder.kt

package com.mochyy.mochymusic.lyrics

import com.mochyy.mochymusic.constants.PreferredLyricsProvider

/**
 * The order lyrics sources are asked in, shared by every platform and written into the settings
 * string exactly as Android always stored it. Which instances exist is the platform's catalogue;
 * this only decides the order of names.
 */
object LyricsSourceOrder {

    /**
     * The built-in order. NetEase, QQ Music and KuGou near the end: they are Chinese services, and
     * LyricsHelper only believes them first-hand for Chinese songs. NetEase leads them because it
     * often has word timing. YouTube's auto captions last of all - they are speech recognition.
     */
    val DefaultOrder: List<String> = listOf(
        "BetterLyrics",
        "BetterLyricsPortato",
        "Unison",
        "YouLyPlus",
        "Paxsenix",
        "LrcRed",
        "BiniLyrics",
        "SimpMusic",
        "LrcLib",
        "Megalobiz",
        "Deezer",
        "YouTubeSubtitle",
        "YouTubeMusic",
        "NetEase",
        "QQMusic",
        "Kugou",
        "Genius",
        "YouTubeCaptions",
    )

    /**
     * A saved order, with any source added since it was saved at the end, in default order.
     * A lyrics module someone just turned on goes first, they added it to be asked.
     */
    fun deserialize(
        orderString: String,
        providerNames: List<String>,
        moduleNames: List<String>,
        defaultOrder: List<String>,
    ): List<String> {
        if (orderString.isBlank()) return moduleNames + defaultOrder
        val saved = orderString.split(",").map { it.trim() }.filter { it in providerNames }.distinct()
        val newModules = moduleNames.filter { it !in saved }
        return newModules + saved + defaultOrder.filter { it !in saved && it !in newModules }
    }

    /** The order lyrics are really asked in: the saved one, or, until someone sorts, the old single preferred pick first. */
    fun effective(
        orderString: String,
        preferred: PreferredLyricsProvider,
        providerNames: List<String>,
        moduleNames: List<String>,
        defaultOrder: List<String>,
    ): List<String> {
        if (orderString.isNotBlank()) return deserialize(orderString, providerNames, moduleNames, defaultOrder)
        val preferredName = providerNameFor(preferred)
        return listOf(preferredName) + (moduleNames + defaultOrder).filter { it != preferredName }
    }

    fun serialize(providers: List<String>, providerNames: List<String>): String =
        providers.filter { it in providerNames }.joinToString(",")

    fun providerNameFor(enum: PreferredLyricsProvider): String = when (enum) {
        PreferredLyricsProvider.LRCLIB -> "LrcLib"
        PreferredLyricsProvider.KUGOU -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
        PreferredLyricsProvider.YOULYPLUS -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX -> "Paxsenix"
    }
}
