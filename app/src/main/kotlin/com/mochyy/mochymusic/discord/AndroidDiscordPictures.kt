// AndroidDiscordPictures.kt
//
// The Android side of the Discord pictures: the rotating artwork's GIF, the canvas GIF and the
// uploads of pictures kept on the phone. The shared presence engine asks for a web address and
// gets null until the picture is ready, exactly as it used to.

package com.mochyy.mochymusic.discord

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AndroidDiscordPictures(
    context: Context,
    onReady: () -> Unit,
) : DiscordPictures {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val spinning = SpinningArtwork(
        scope = scope,
        onReady = onReady,
        loadLocal = { source, size -> loadLocalPicture(appContext, source, size) },
    )
    private val uploads = UploadedPictures(appContext, scope, onReady = onReady)
    private val canvas = CanvasGif(appContext, onReady = onReady)

    override fun spinningUrl(sourceUrl: String, style: SpinStyle): String? =
        spinning.urlFor(sourceUrl, style)

    override fun uploadedUrl(sourceUrl: String): String? =
        uploads.urlFor(sourceUrl)

    override fun canvasUrl(
        title: String,
        artist: String,
        durationSec: Int?,
        source: PresencePicture.CanvasSource,
        fps: Int,
        size: Int,
    ): String? = canvas.urlFor(title, artist, durationSec, source, fps, size)
}
