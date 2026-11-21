// SharedLyricsProviders.kt
// BiniLyrics and Megalobiz, ported from Natune (github.com/NatuneGroup/Natune, GPL-3.0); the lookups
// themselves live in :shared so the desktop and iOS apps can use the same code later.

package com.mochyy.mochymusic.lyrics

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.betterlyrics.TTMLParser
import com.mochyy.mochymusic.constants.EnableBiniLyricsKey
import com.mochyy.mochymusic.constants.EnableMegalobizKey
import com.mochyy.mochymusic.shared.lyrics.BiniLyricsProvider as SharedBini
import com.mochyy.mochymusic.shared.lyrics.LyricsPayload
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider as SharedLyricsProvider
import com.mochyy.mochymusic.shared.lyrics.MegalobizLyricsProvider as SharedMegalobiz
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One client for every shared provider: they never build an engine of their own. */
internal object SharedLyricsHttp {
    val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
            expectSuccess = false
        }
    }
}

/**
 * Wraps a :shared provider in the app's [LyricsProvider]. Word-timed answers can come back as TTML;
 * the lyrics screen reads LRC, so those go through the same TTML parser lrc.red uses.
 */
open class SharedLyricsProviderAdapter(
    override val name: String,
    private val enabledKey: Preferences.Key<Boolean>,
    create: (HttpClient) -> SharedLyricsProvider,
) : LyricsProvider {
    private val delegate by lazy { create(SharedLyricsHttp.client) }

    override fun isEnabled(context: Context): Boolean = context.dataStore[enabledKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        delegate.getLyrics(id, title, artist, album, duration).mapCatching { raw ->
            if (!LyricsPayload.isTtmlDocument(raw)) return@mapCatching raw
            val lines = TTMLParser.parseTTML(raw)
            check(lines.isNotEmpty()) { "$name has no timed lyrics for this track" }
            TTMLParser.toLRC(lines)
        }
    }
}

object BiniLyricsProvider : SharedLyricsProviderAdapter(
    name = "BiniLyrics",
    enabledKey = EnableBiniLyricsKey,
    create = ::SharedBini,
)

object MegalobizLyricsProvider : SharedLyricsProviderAdapter(
    name = "Megalobiz",
    enabledKey = EnableMegalobizKey,
    create = ::SharedMegalobiz,
)
