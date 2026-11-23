// LyricsProviderRegistry.kt

package com.mochyy.mochymusic.lyrics

import com.mochyy.mochymusic.constants.PreferredLyricsProvider

/**
 * The lyrics sources Android has: thin wrappers over the Android provider modules, the three
 * YouTube sources and the shared Natune providers, plus lyrics modules while they are on.
 *
 * The ordering rules live in shared LyricsSourceOrder (and every platform sorts the same way);
 * this object only says which instances exist and how each is named on screen.
 */
object LyricsProviderRegistry : LyricsProviderCatalog {
    init {
        // the shared translate flow drives the app's api/ back ends and the video-caption lookup;
        // the first lyrics use of any kind is what needs them
        LyricsTranslationHelper.install(AndroidLyricsTranslationServices, AndroidLyricsCaptionTranslation)
    }

    private val providerMap = mapOf(
        "YouLyPlus"       to YouLyPlusLyricsProvider,
        "Paxsenix"        to PaxsenixLyricsProvider,
        "BetterLyrics"    to BetterLyricsProvider,
        "Unison"          to UnisonLyricsProvider,
        "SimpMusic"       to SimpMusicLyricsProvider,
        "LrcLib"          to LrcLibLyricsProvider,
        "Kugou"           to KuGouLyricsProvider,
        "NetEase"         to NetEaseLyricsProvider,
        "QQMusic"         to QQMusicLyricsProvider,
        "Deezer"          to DeezerLyricsProvider,
        "LrcRed"          to LrcRedLyricsProvider,
        "BiniLyrics"      to BiniLyricsProvider,
        "Megalobiz"       to MegalobizLyricsProvider,
        "BetterLyricsPortato" to BetterLyricsPortatoProvider,
        "Genius"          to GeniusLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTubeMusic"    to YouTubeLyricsProvider,
        "YouTubeCaptions" to YouTubeCaptionsLyricsProvider,
    )

    // lyrics from modules that are on, set by the module registry; a module switched off simply isn't in here
    @Volatile
    private var moduleProviders: Map<String, ModuleLyricsProvider> = emptyMap()

    fun setModuleProviders(providers: List<ModuleLyricsProvider>) {
        moduleProviders = providers.associateBy { it.name }
    }

    override val providerNames: List<String> get() = moduleProviders.keys.toList() + providerMap.keys

    override fun getProviderByName(name: String): LyricsProvider? = providerMap[name] ?: moduleProviders[name]

    /** A saved order, with any source added since it was saved at the end, in default order. */
    override fun deserializeProviderOrder(orderString: String): List<String> =
        LyricsSourceOrder.deserialize(orderString, providerNames, moduleProviders.keys.toList(), LyricsSourceOrder.DefaultOrder)

    // the order lyrics are really asked in: the saved one, or until someone sorts, the old single preferred pick first
    override fun effectiveProviderOrder(orderString: String, preferred: PreferredLyricsProvider): List<String> =
        LyricsSourceOrder.effective(
            orderString,
            preferred,
            providerNames,
            moduleProviders.keys.toList(),
            LyricsSourceOrder.DefaultOrder,
        )

    fun serializeProviderOrder(providers: List<String>): String =
        LyricsSourceOrder.serialize(providers, providerNames)

    // NetEase, QQ Music and KuGou near the end: they are Chinese services, and LyricsHelper only
    // believes them first-hand for Chinese songs. NetEase leads them because it often has word
    // timing. YouTube's auto captions last of all - they are speech recognition.
    fun getDefaultProviderOrder(): List<String> = moduleProviders.keys.toList() + LyricsSourceOrder.DefaultOrder

    fun getProviderNameForEnum(enum: PreferredLyricsProvider): String = LyricsSourceOrder.providerNameFor(enum)

    fun getDisplayName(name: String): String = when (name) {
        "YouLyPlus"       -> "YouLyPlus"
        "Paxsenix"        -> "PaxSenix"
        "BetterLyrics"    -> "Apple Music"
        "Unison"          -> "Unison"
        "SimpMusic"       -> "SimpMusic"
        "LrcLib"          -> "LrcLib"
        "Kugou"           -> "KuGou"
        "NetEase"         -> "NetEase"
        "QQMusic"         -> "QQ Music"
        "Deezer"          -> "Deezer"
        "LrcRed"          -> "lrc.red"
        "BiniLyrics"      -> "BiniLyrics"
        "Megalobiz"       -> "Megalobiz"
        "BetterLyricsPortato" -> "BetterLyrics Portato"
        "Genius"          -> "Genius"
        "YouTubeSubtitle" -> "YouTube Subtitle"
        "YouTubeMusic"    -> "YouTube Music"
        "YouTubeCaptions" -> "YouTube Auto Captions"
        else              -> moduleProviders[name]?.displayName ?: name
    }

    /**
     * The open-source project behind a source, for the lyrics page's "Star on GitHub" button.
     * Only sources whose code is public: KuGou, NetEase, QQ Music, Deezer and YouTube are the
     * companies' own services, and Paxsenix hasn't published its server. SimpMusic's lyrics
     * server has no public repo of its own, so its star goes to the SimpMusic app.
     */
    fun getProject(name: String): LyricsProject? = when (name) {
        "YouLyPlus"    -> LyricsProject("LyricsPlus", "https://github.com/ibratabian17/LyricsPlus")
        "BetterLyrics" -> LyricsProject("Better Lyrics", "https://github.com/better-lyrics/better-lyrics")
        "Unison"       -> LyricsProject("Unison", com.music.directlyrics.Unison.RepoUrl)
        "LrcLib"       -> LyricsProject("LRCLIB", "https://github.com/tranxuanthang/lrclib")
        "SimpMusic"    -> LyricsProject("SimpMusic", "https://github.com/maxrave-dev/SimpMusic")
        else           -> null
    }

    // lrc.red and unison require linking back wherever their lyrics are shown
    fun getWebsite(name: String): String? = when (name) {
        "LrcRed" -> com.music.directlyrics.LrcRed.BaseUrl
        "BiniLyrics" -> "https://lyrics-api.binimum.org/"
        "Megalobiz" -> "https://www.megalobiz.com/"
        "Genius" -> "https://genius.com/"
        "Unison" -> com.music.directlyrics.Unison.BaseUrl
        else     -> null
    }
}

data class LyricsProject(val name: String, val repoUrl: String)
