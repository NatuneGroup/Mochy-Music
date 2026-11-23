package com.mochyy.mochymusic.eq.audio

import kotlin.math.floor

/**
 * rounded, not cut toward zero: cutting adds a faint grit to quiet passages.
 * Half values round up (floor(x + 0.5)), the same as the java.lang.Math.round this used to call.
 */
fun toPcm(sample: Double): Short =
    floor(sample * 32768.0 + 0.5).coerceIn(-32768.0, 32767.0).toShort()
