// LyricsCaptionTranslation.kt
//
// The video's own uploaded translation, as the shared translate flow asks for it. Finding the
// official music video is app/ui code (catalog helpers the lyrics screen already has), so the
// lookup itself stays in :app behind this bridge.

package com.mochyy.mochymusic.lyrics

/** What the YouTube caption translation path needs to know about the song. */
data class YouTubeCaptionRequest(
    val songId: String,
    val title: String,
    val artist: String,
    /** The track is the video itself, so no search is needed to find it. */
    val isVideo: Boolean,
    val lines: List<LyricsEntry>,
    val targetLanguage: String,
    /** Where the song sits inside a separate music video, by listening to both, for when its captions can't be lined up with the lyrics. */
    val matchAudio: (suspend (videoId: String) -> SongVideoMap?)? = null,
)

/** The app's YouTubeCaptionTranslation, behind shared code. Null lines mean YouTube has nothing to offer. */
interface LyricsCaptionTranslation {
    suspend fun translate(request: YouTubeCaptionRequest): List<String?>?
}

/**
 * What a stored YouTube translation is filed under, kept apart from Google's and the AI's. The
 * Android YouTubeCaptionTranslation reads the same tag.
 */
fun youTubeTranslationLanguageTag(targetLanguage: String): String = "yt:$targetLanguage"
