// SharedLyricsProviders.kt
// BiniLyrics, Megalobiz, BetterLyrics Portato and Genius, ported from Natune (github.com/NatuneGroup/Natune, GPL-3.0); the lookups
// themselves live in :shared so the desktop and iOS apps can use the same code too.

package com.mochyy.mochymusic.lyrics

import com.mochyy.mochymusic.shared.lyrics.BetterLyricsPortatoProvider as SharedBetterLyricsPortato
import com.mochyy.mochymusic.shared.lyrics.BiniLyricsProvider as SharedBini
import com.mochyy.mochymusic.shared.lyrics.GeniusLyricsProvider as SharedGenius
import com.mochyy.mochymusic.shared.lyrics.LyricsPayload
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider as SharedLyricsProvider
import com.mochyy.mochymusic.shared.lyrics.MegalobizLyricsProvider as SharedMegalobiz
import com.mochyy.mochymusic.shared.lyrics.TtmlParser
import io.ktor.client.HttpClient

/**
 * Wraps a :shared provider in the app's [LyricsProvider]. Word-timed answers can come back as TTML;
 * the lyrics screen reads LRC, so those go through the same TTML parser lrc.red uses.
 */
open class SharedLyricsProviderAdapter(
    override val name: String,
    create: (HttpClient) -> SharedLyricsProvider,
) : LyricsProvider {
    private val delegate by lazy { create(lyricsHttpClient()) }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = delegate.getLyrics(id, title, artist, album, duration).mapCatching { raw ->
        if (!LyricsPayload.isTtmlDocument(raw)) {
            // other XML (a QRC document, an HTML error page) is not lyrics the screen can show
            check(!raw.trimStart().startsWith("<")) { "$name answered with a document that isn't lyrics" }
            return@mapCatching raw
        }
        val lines = TtmlParser.parseTtml(raw)
        check(lines.isNotEmpty()) { "$name has no timed lyrics for this track" }
        TtmlParser.toLrc(lines)
    }
}

object BiniLyricsProvider : SharedLyricsProviderAdapter(
    name = "BiniLyrics",
    create = ::SharedBini,
)

object MegalobizLyricsProvider : SharedLyricsProviderAdapter(
    name = "Megalobiz",
    create = ::SharedMegalobiz,
)

object BetterLyricsPortatoProvider : SharedLyricsProviderAdapter(
    name = "BetterLyricsPortato",
    create = ::SharedBetterLyricsPortato,
)

object GeniusLyricsProvider : SharedLyricsProviderAdapter(
    name = "Genius",
    // off by default, as in Natune: Genius has plain text only, no timing
    create = ::SharedGenius,
)
