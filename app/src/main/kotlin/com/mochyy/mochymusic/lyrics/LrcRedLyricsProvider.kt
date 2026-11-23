// LrcRedLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import com.music.directlyrics.LrcRed
import com.mochyy.mochymusic.betterlyrics.TTMLParser

object LrcRedLyricsProvider : LyricsProvider {
    override val name = "LrcRed"

    // lrc.red speaks the same ttml as apple music, so the better lyrics parser keeps the word timing, singers and background vocals
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = LrcRed.getTtml(title, artist, duration).mapCatching { ttml ->
        val lines = TTMLParser.parseTTML(ttml)
        check(lines.isNotEmpty()) { "lrc.red has no timed lyrics for this track" }
        TTMLParser.toLRC(lines)
    }
}
