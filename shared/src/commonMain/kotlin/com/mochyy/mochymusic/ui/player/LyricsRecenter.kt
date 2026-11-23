package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.CubicBezierEasing

const val RecenterDurationMs = 950

/** Shared with LyricsInterludeDots, whose collapse has to run in step with the recenter scroll. */
const val LyricsRecenterDurationMs = RecenterDurationMs

/** Style 2's recenter curve (the original). */
val LyricsRecenterEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)
