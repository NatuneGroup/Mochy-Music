package com.mochyy.mochymusic.lyrics

import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint

actual fun platformLyricsYouTube(): LyricsYouTube = AndroidLyricsYouTube

/** The same calls YouTubeLyricsProvider / YouTubeSubtitleLyricsProvider / YouTubeCaptionsLyricsProvider made. */
private object AndroidLyricsYouTube : LyricsYouTube {
    override suspend fun musicLyrics(videoId: String): Result<String> = runCatching {
        val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrThrow()
        YouTube
            .lyrics(
                endpoint = nextResult.lyricsEndpoint
                    ?: throw IllegalStateException("Lyrics endpoint not found"),
            ).getOrThrow() ?: throw IllegalStateException("Lyrics unavailable")
    }

    override suspend fun subtitleTranscript(videoId: String): Result<String> = YouTube.transcript(videoId)

    override suspend fun autoCaptions(videoId: String): Result<String> = YouTube.captions(videoId)
}
