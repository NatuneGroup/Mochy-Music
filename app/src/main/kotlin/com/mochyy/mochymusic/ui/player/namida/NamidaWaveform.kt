// namida's seek bar: a waveform of rounded bars, the played part a gradient of the song's
// colour, tap to seek, drag to scrub with the +mm:ss delta shown, and a drag up that cancels.

package com.mochyy.mochymusic.ui.player.namida

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.mochyy.mochymusic.ui.player.styles.LivePosition
import com.mochyy.mochymusic.utils.makeTimeString
import kotlin.math.abs

/** namida's waveform: how tall the bar field is, and the padding it keeps from the edges. */
private val NamidaWaveformHeight = 64.dp
private val NamidaWaveformPadding = 16.dp

/** the share of a bar's slot the bar itself takes, and the shortest a bar may be */
private const val NamidaBarWidthFraction = 0.54f
private val NamidaMinBarHeight = 3.dp
private val NamidaBarCornerRadius = 5.dp
private val NamidaBarWidth = 3.dp

/**
 * The bars themselves: namida extracts a waveform from each file; sora has no such extraction,
 * so the bars are shaped from the song's id — steady wherever the song is played again, and
 * different from song to song, which is what the eye reads.
 */
internal fun namidaBars(songKey: String, count: Int): FloatArray {
    if (count <= 0) return FloatArray(0)
    val amplitudes = FloatArray(count)
    var seed = 0L
    for (byte in songKey.toByteArray()) seed = seed * 131L + byte
    if (seed == 0L) seed = 1L
    var state = seed and 0x7FFFFFFFFFFFFFFFL
    fun next(): Float {
        state = state * 6364136223846793005L + 1442695040888963407L and 0x7FFFFFFFFFFFFFFFL
        return (state ushr 40).toFloat() / 0xFFFFFFL
    }
    // a moving walk over the noise, so the bars rise and fall in runs the way music does
    var walk = 0.5f
    for (i in 0 until count) {
        walk = (walk * 0.72f + next() * 0.28f).coerceIn(0f, 1f)
        // namida's bars lean tall: few sit near the floor, most in the upper half
        amplitudes[i] = (0.28f + walk * 0.72f).coerceIn(0.12f, 1f)
    }
    return amplitudes
}

/**
 * The played region's gradient, namida's recipe: the song's colour blended over the ink at two
 * strengths, the brighter end where playback stands.
 */
private fun playedBrush(palette: NamidaPalette, width: Float): Brush = Brush.horizontalGradient(
    colors = listOf(
        palette.accent.copy(alpha = 0.71f).compositeOver(palette.onSurface.copy(alpha = 0.43f)),
        palette.accent.copy(alpha = 0.55f).compositeOver(palette.onSurface.copy(alpha = 0.43f)),
    ),
    startX = 0f,
    endX = width.coerceAtLeast(1f),
)

/**
 * The waveform the player scrubs. [onSeek] lands a tap or a drag's release; while a drag is held
 * the bars follow the finger and the delta is shown over them.
 */
@Composable
internal fun NamidaWaveform(
    palette: NamidaPalette,
    position: LivePosition,
    durationMs: Long,
    songKey: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var scrubFraction by remember { mutableFloatStateOf(Float.NaN) }
    var scrubDeltaMs by remember { mutableStateOf(0L) }
    var cancelled by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NamidaWaveformPadding)
            .height(NamidaWaveformHeight),
    ) {
        Canvas(Modifier.fillMaxWidth().height(NamidaWaveformHeight)) {
            val fraction = if (scrubFraction.isNaN()) {
                (position.positionMs().toFloat() / durationMs).coerceIn(0f, 1f)
            } else {
                scrubFraction
            }
            drawBars(palette, songKey, fraction)
        }
        if (!scrubFraction.isNaN() && !cancelled) {
            // the centered +mm:ss of the drag, namida's scrub readout
            val text = (if (scrubDeltaMs >= 0) "+" else "−") + makeTimeString(abs(scrubDeltaMs))
            Text(
                text = text,
                color = palette.onSurface,
                fontSize = 13.sp,
                fontFamily = NamidaFontFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // the gestures: a tap seeks, a drag scrubs and shows its delta, five dp up cancels
        Box(
            Modifier
                .fillMaxWidth()
                .height(NamidaWaveformHeight)
                .pointerInput(durationMs, songKey) {
                    detectTapGestures { change ->
                        val fraction = (change.x / size.width).coerceIn(0f, 1f)
                        onSeek((fraction * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs, songKey) {
                    var startY = 0f
                    detectDragGestures(
                        onDragStart = { change ->
                            cancelled = false
                            scrubDeltaMs = 0L
                            startY = change.y
                            scrubFraction = (change.x / size.width).coerceIn(0f, 1f)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDrag = { change, _ ->
                            if (cancelled) return@detectDragGestures
                            // namida cancels the seek when the finger climbs off the bars
                            if (change.position.y < startY - 5.dp.toPx()) {
                                cancelled = true
                                scrubFraction = Float.NaN
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                return@detectDragGestures
                            }
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            scrubDeltaMs = (fraction * durationMs).toLong() - position.positionMs()
                            scrubFraction = fraction
                        },
                        onDragEnd = {
                            if (!cancelled && !scrubFraction.isNaN()) {
                                onSeek((scrubFraction * durationMs).toLong())
                            }
                            scrubFraction = Float.NaN
                        },
                        onDragCancel = { scrubFraction = Float.NaN },
                    )
                },
        )
    }
}

/** one field of rounded bars, split at [fraction]: played in the song's colour, the rest in inks. */
private fun DrawScope.drawBars(palette: NamidaPalette, songKey: String, fraction: Float) {
    val slotWidth = NamidaBarWidth.toPx() / NamidaBarWidthFraction
    val count = (size.width / slotWidth).toInt().coerceAtLeast(1)
    val bars = namidaBars(songKey, count)
    val barWidth = NamidaBarWidth.toPx()
    val playedWidth = size.width * fraction.coerceIn(0f, 1f)
    val played = playedBrush(palette, playedWidth)
    val unplayedFront = palette.onSurface.copy(alpha = 0.43f)
    val unplayedBehind = palette.onSurface.copy(alpha = 0.16f)
    val radius = CornerRadius(NamidaBarCornerRadius.toPx())
    val playedEdge = playedWidth + slotWidth
    for (i in 0 until count) {
        val x = i * slotWidth
        val amplitude = bars[i]
        val barHeight = (NamidaMinBarHeight.toPx() + (size.height - NamidaMinBarHeight.toPx()) * amplitude)
            .coerceAtMost(size.height)
        val topLeft = Offset(x, (size.height - barHeight) / 2f)
        when {
            // the played bars share the song's gradient
            x + barWidth / 2f <= playedWidth -> drawRoundRect(
                brush = played,
                topLeft = topLeft,
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
            // just past the playhead the ink is stronger, fading further out, namida's two tones
            x < playedEdge -> drawRoundRect(color = unplayedFront, topLeft = topLeft, size = Size(barWidth, barHeight), cornerRadius = radius)
            else -> drawRoundRect(color = unplayedBehind, topLeft = topLeft, size = Size(barWidth, barHeight), cornerRadius = radius)
        }
    }
    // the playhead's edge, a light catch on the boundary
    drawRect(
        color = Color.White.copy(alpha = 0.10f),
        topLeft = Offset(playedWidth - 1f, 0f),
        size = Size(1.5f, size.height),
    )
}
