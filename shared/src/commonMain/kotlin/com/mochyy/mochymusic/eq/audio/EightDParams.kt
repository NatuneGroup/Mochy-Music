package com.mochyy.mochymusic.eq.audio

/** what the 8D effect plays: how fast the song circles the head and how far it leans either way */
data class EightDParams(
    val enabled: Boolean = false,
    val rotationSpeedHz: Float = 0.15f,
    val depth: Float = 0.85f,
    val reverbLevel: Float = 0.4f,
)
