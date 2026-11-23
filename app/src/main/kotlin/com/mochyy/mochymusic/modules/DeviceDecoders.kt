// DeviceDecoders.kt

package com.mochyy.mochymusic.modules

import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.mochyy.mochymusic.module.SourceStream
import java.util.concurrent.ConcurrentHashMap

/** whether a module's stream can be decoded here, by the phone or our ffmpeg; one nothing takes plays on in silence, so it's passed over (docs/notes/audio-output.md) */
object DeviceDecoders {
    // the shared code names the codecs and their mime types; the answer is this phone's
    private val codecs = DeviceCodecs(
        object : DecoderPlatform {
            // asked once per mime and rate, the way the decoder list and ffmpeg could have been before
            private val known = ConcurrentHashMap<String, Boolean>()

            override fun canPlay(mime: String, sampleRateHz: Int): Boolean =
                known.getOrPut("$mime@$sampleRateHz") { hasDecoder(mime, sampleRateHz) || ffmpegDecodes(mime) }
        },
    )

    fun canPlay(stream: SourceStream): Boolean = canPlay(stream.codec, stream.sampleRateHz ?: 0)

    /** [codec] ("FLAC", "ALAC"...) at [sampleRateHz], or at any rate when 0 */
    fun canPlay(codec: String?, sampleRateHz: Int = 0): Boolean = codecs.canPlay(codec, sampleRateHz)
}

/** a decoder for [mime] that takes [sampleRateHz] (any rate when 0); true when it can't be told */
private fun hasDecoder(mime: String, sampleRateHz: Int): Boolean = runCatching {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        !info.isEncoder &&
            info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
            (
                sampleRateHz <= 0 ||
                    runCatching {
                        info.getCapabilitiesForType(mime).audioCapabilities?.isSampleRateSupported(sampleRateHz) != false
                    }.getOrDefault(true)
                )
    }
}.getOrDefault(true)

// ffmpeg takes any sample rate, so only the codec is asked; false if its library didn't load
@OptIn(UnstableApi::class)
private fun ffmpegDecodes(mime: String): Boolean =
    runCatching { FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(mime) }.getOrDefault(false)
