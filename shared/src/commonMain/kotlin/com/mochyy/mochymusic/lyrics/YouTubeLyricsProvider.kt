// YouTubeLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

object YouTubeLyricsProvider : LyricsProvider {
    // Must match the registry key (see YouTubeSubtitleLyricsProvider): a spaced name never
    // matched the registry, so this source could not be picked or marked as in use.
    override val name = "YouTubeMusic"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = platformLyricsYouTube().musicLyrics(id)
}
