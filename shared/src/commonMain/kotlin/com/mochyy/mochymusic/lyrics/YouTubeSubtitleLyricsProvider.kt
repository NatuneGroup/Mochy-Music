// YouTubeSubtitleLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    // Must match the registry key, not the display name: this string is what gets persisted on
    // LyricsEntity.provider and later fed back through LyricsProviderRegistry, which only knows
    // the un-spaced form. See LyricsProviderRegistry.getDisplayName for the pretty version.
    override val name = "YouTubeSubtitle"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = platformLyricsYouTube().subtitleTranscript(id)
}
