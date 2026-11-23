package com.mochyy.mochymusic.eq.audio

/** loudness: boostDb of gain, compression 0..1 of lifting the quiet parts */
data class LoudnessParams(
    val enabled: Boolean = false,
    val boostDb: Float = 4f,
    val compression: Float = 0.3f,
)
