package com.mochyy.mochymusic.ui.player.video

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// how wide a strip at each side of the picture its edge colour is taken from
private const val EdgeBand = 0.12f

// the colours at the left and right edges of the picture, for the light round it
@Immutable
data class VideoAmbient(val left: Color, val right: Color)

// the average colour of a strip down each side of the picture, inside any bars
fun edgeColors(pixels: IntArray, size: Int, insetX: Float, insetY: Float): VideoAmbient {
    val top = (insetY * size).toInt().coerceIn(0, size / 2 - 1)
    val bottom = size - top
    val left = (insetX * size).toInt().coerceIn(0, size / 2 - 1)
    val right = size - left
    val band = ((right - left) * EdgeBand).toInt().coerceAtLeast(2)

    fun average(from: Int, to: Int): Color {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (y in top until bottom step 2) {
            val row = y * size
            for (x in from until to) {
                val c = pixels[row + x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                count++
            }
        }
        if (count == 0) return Color.Black
        return Color(red = (r / count).toInt(), green = (g / count).toInt(), blue = (b / count).toInt())
    }
    return VideoAmbient(left = average(left, left + band), right = average(right - band, right))
}
