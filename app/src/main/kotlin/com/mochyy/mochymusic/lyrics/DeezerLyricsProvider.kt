// DeezerLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import com.music.directlyrics.Deezer

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Deezer.getLyrics(title, artist, duration)
}
