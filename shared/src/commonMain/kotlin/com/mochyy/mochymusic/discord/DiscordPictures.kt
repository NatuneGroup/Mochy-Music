// DiscordPictures.kt
//
// The pictures that can only be made on the device: the rotating artwork's GIF, the canvas (or
// Apple Music animated cover) GIF, and the upload of a picture kept on the phone. Each returns a
// web address once it's ready and null while it's still being made, so the presence engine can
// fall back to the still artwork in the meantime. Android makes them with Canvas and media3;
// desktop and iOS can hand in the same three calls when they show a Discord status.

package com.mochyy.mochymusic.discord

interface DiscordPictures {
    /** A web address for the rotating artwork of [sourceUrl], or null while its GIF is being made. */
    fun spinningUrl(sourceUrl: String, style: SpinStyle): String?

    /** A web address for a picture kept on the phone (a local cover, a picked one), or null while it uploads. */
    fun uploadedUrl(sourceUrl: String): String?

    /** A web address for a GIF of this song's canvas, or null while it's being made. */
    fun canvasUrl(
        title: String,
        artist: String,
        durationSec: Int?,
        source: PresencePicture.CanvasSource,
        fps: Int,
        size: Int,
    ): String?
}

/** Nothing can be made on this platform: every picture falls back to its still. */
object NoDiscordPictures : DiscordPictures {
    override fun spinningUrl(sourceUrl: String, style: SpinStyle): String? = null

    override fun uploadedUrl(sourceUrl: String): String? = null

    override fun canvasUrl(
        title: String,
        artist: String,
        durationSec: Int?,
        source: PresencePicture.CanvasSource,
        fps: Int,
        size: Int,
    ): String? = null
}
