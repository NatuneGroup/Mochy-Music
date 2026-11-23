package com.mochyy.mochymusic.eq.audio

enum class ReplayGainMode { Track, Album }

/** how replaygain is applied: which of the file's two gains, and a preamp on top */
data class ReplayGainParams(
    val mode: ReplayGainMode = ReplayGainMode.Track,
    /** monochrome's default: replaygain's 89 db reference sits a little quiet for phones */
    val preampDb: Float = 3f,
)
