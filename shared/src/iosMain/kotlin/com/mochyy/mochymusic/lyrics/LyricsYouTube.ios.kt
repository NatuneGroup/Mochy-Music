package com.mochyy.mochymusic.lyrics

actual fun platformLyricsYouTube(): LyricsYouTube = UnavailableLyricsYouTube

/** YouTube browsing is not in the iOS build yet; every lyrics lookup fails the way it used to. */
private object UnavailableLyricsYouTube : LyricsYouTube {
    private fun unavailable(): Result<String> = Result.failure(LyricsYouTubeUnavailableException())

    override suspend fun musicLyrics(videoId: String): Result<String> = unavailable()

    override suspend fun subtitleTranscript(videoId: String): Result<String> = unavailable()

    override suspend fun autoCaptions(videoId: String): Result<String> = unavailable()
}
