// whether a module's stream can be played here: the mime types each codec name stands for, asked of the platform's own decoders

package com.mochyy.mochymusic.modules

/** what a platform can decode: its own decoders and its ffmpeg, remembered per mime and sample rate by the platform */
interface DecoderPlatform {
    /** whether [mime] at [sampleRateHz] (any rate when 0) can be decoded here */
    fun canPlay(mime: String, sampleRateHz: Int): Boolean
}

/**
 * A module names its stream's codec ("FLAC", "ALAC", "AC-3"…), and one nothing here can decode plays on in silence, so it's
 * passed over for the next provider. Every codec that isn't listed is taken as playable: the phones decode the rest.
 */
class DeviceCodecs(private val platform: DecoderPlatform) {

    fun canPlay(codec: String?, sampleRateHz: Int = 0): Boolean {
        val mimes = when (codec?.uppercase()) {
            "FLAC" -> listOf("audio/flac")
            "ALAC" -> listOf("audio/alac")
            // media3 plays atmos on a plain e-ac-3 decoder too, it just folds the height channels into the bed
            "DOLBY ATMOS" -> listOf("audio/eac3-joc", "audio/eac3")
            "E-AC-3" -> listOf("audio/eac3")
            "AC-3" -> listOf("audio/ac3")
            // AAC and the rest: every phone decodes them
            else -> return true
        }
        return mimes.any { mime -> platform.canPlay(mime, sampleRateHz) }
    }
}
