// NetEaseLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import com.music.directlyrics.NetEase

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = NetEase.getLyrics(title, artist, duration)
}
