// QQMusicLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import com.music.directlyrics.QQMusic

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
