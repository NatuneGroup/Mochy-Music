// AndroidLyricsCaptionTranslation.kt
//
// The video's uploaded translation lookup as the shared helper asks for it: the app's
// YouTubeCaptionTranslation still finds the video and aligns the cues, this only hands it the
// request in the shape it already takes.

package com.mochyy.mochymusic.lyrics

object AndroidLyricsCaptionTranslation : LyricsCaptionTranslation {
    override suspend fun translate(request: YouTubeCaptionRequest): List<String?>? =
        YouTubeCaptionTranslation.translate(
            YouTubeCaptionTranslation.Request(
                songId = request.songId,
                title = request.title,
                artist = request.artist,
                isVideo = request.isVideo,
                lines = request.lines,
                targetLanguage = request.targetLanguage,
                matchAudio = request.matchAudio,
            ),
        )
}
