// LyricsYouTube.kt
//
// YouTube's own lyrics, subtitle transcript and captions, behind the platform: innertube's YouTube
// object exists for Android and desktop only, so common code asks this and each JVM platform
// delegates to the same calls the app made. iOS has no YouTube browsing, so its lookups fail
// cleanly like every other YouTube request there.

package com.mochyy.mochymusic.lyrics

interface LyricsYouTube {
    /** YouTube Music's lyrics for the video, or a failure when the video has none. */
    suspend fun musicLyrics(videoId: String): Result<String>

    /** The video's subtitle transcript as timed lines. */
    suspend fun subtitleTranscript(videoId: String): Result<String>

    /** The video's captions (auto-generated or uploaded) as timed lines. */
    suspend fun autoCaptions(videoId: String): Result<String>
}

class LyricsYouTubeUnavailableException :
    Exception("YouTube lyrics are not available on iOS yet")

expect fun platformLyricsYouTube(): LyricsYouTube
