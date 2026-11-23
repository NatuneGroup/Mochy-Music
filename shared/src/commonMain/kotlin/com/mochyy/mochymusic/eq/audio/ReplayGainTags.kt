package com.mochyy.mochymusic.eq.audio

import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlin.math.pow

/** one player's song replaygain from its file (monochrome flacs, many local files), read from vorbis comments on track change; it can only bring a song down and never takes a peak past full scale */
class ReplayGainTags {
    @Volatile var trackGain: Float? = null
        private set
    @Volatile var trackPeak: Float? = null
        private set
    @Volatile var albumGain: Float? = null
        private set
    @Volatile var albumPeak: Float? = null
        private set

    val present: Boolean get() = trackGain != null || albumGain != null

    /** from the song's vorbis comments (key, value); a song without any clears the last one's */
    fun update(comments: List<Pair<String, String>>) {
        var tg: Float? = null
        var tp: Float? = null
        var ag: Float? = null
        var ap: Float? = null
        for ((key, text) in comments) {
            val value = number(text) ?: continue
            when (key.uppercase()) {
                "REPLAYGAIN_TRACK_GAIN" -> tg = value
                "REPLAYGAIN_TRACK_PEAK" -> tp = value
                "REPLAYGAIN_ALBUM_GAIN" -> ag = value
                "REPLAYGAIN_ALBUM_PEAK" -> ap = value
            }
        }
        trackGain = tg
        trackPeak = tp
        albumGain = ag
        albumPeak = ap
    }

    /** the volume factor for params; 1 when there's nothing to apply */
    fun linear(params: ReplayGainParams, normalize: Boolean): Float {
        if (!normalize) return 1f
        val album = params.mode == ReplayGainMode.Album
        val gain = (if (album) albumGain ?: trackGain else trackGain ?: albumGain) ?: return 1f
        val peak = if (album) albumPeak ?: trackPeak else trackPeak ?: albumPeak
        var factor = 10f.pow((gain + params.preampDb) / 20f)
        if (peak != null && peak > 0f) factor = min(factor, 1f / peak)
        return factor.coerceIn(0.03f, 1f)
    }

    companion object {
        /** "-8.89 db", "1.000000", "+2.1 db" */
        internal fun number(text: String): Float? =
            Regex("""[-+]?\d+(?:\.\d+)?""").find(text)?.value?.toFloatOrNull()
    }
}
