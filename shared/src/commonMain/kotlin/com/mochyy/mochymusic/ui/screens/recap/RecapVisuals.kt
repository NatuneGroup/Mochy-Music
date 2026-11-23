// RecapVisuals.kt
//
// The recap visuals every platform shares: the one clock the page and the story share, the giant
// slow-moving words behind everything, and the brightened text colours for a palette. The parts
// that name a platform API (the Palette API and the AGSL mesh gradient) stay in app's
// RecapPalettes.kt. The eases, palette state, giant-word state and dot grid are in RecapStoryDrawing.kt.

package com.mochyy.mochymusic.ui.screens.recap

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.component.monotonicMillis
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.theme.colorToHSL
import com.mochyy.mochymusic.ui.theme.hslToColor
import kotlinx.coroutines.flow.first
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Clock

/**
 * One time base for the page and the story. The giant words' positions are a pure function of it,
 * so when the story takes over from the page the column carries on exactly where it was.
 */
internal object RecapClock {
    // the platform's monotonic clock, in nanoseconds: monotonicMillis is System.nanoTime to the millisecond
    private val epoch = monotonicMillis() * 1_000_000L
    fun seconds(frameNanos: Long): Double = (frameNanos - epoch) / 1_000_000_000.0
}

@Stable
class RecapTime : RecapTimeSource {
    var frameNanos by mutableLongStateOf(monotonicMillis() * 1_000_000L)

    override val seconds: Double get() = RecapClock.seconds(frameNanos)

    /** Seconds wrapped to a small range, for sin/cos sway where float precision matters more than continuity. */
    override val sway: Float get() = (seconds % 3600.0).toFloat()
}

/**
 * Ticks every frame while [running] and not [held]. Read it only inside draw/graphicsLayer lambdas.
 * [held] is read as a lambda so flipping it never recomposes the caller.
 */
@Composable
fun rememberRecapTime(running: Boolean = true, held: () -> Boolean = { false }): RecapTime {
    val time = remember { RecapTime() }
    val isHeld by rememberUpdatedState(held)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            if (isHeld()) snapshotFlow { isHeld() }.first { !it }
            withInfiniteAnimationFrameNanos { time.frameNanos = it }
        }
    }
    return time
}

// ---------------------------------------------------------------------------------------------
// Giant words

/** The text-plane font size: one size everywhere, so a column handed between screens matches. */
internal val GiantFontSize = 178.dp

/** Half-resolution bitmaps of each word, so a 178dp glyph run is a textured quad, not a path fill. */
private class GiantWordCache(private val measurer: TextMeasurer) {
    class Word(val image: ImageBitmap, val tileWidth: Float, val drawSize: IntSize, val capCenterY: Float)

    private val words = HashMap<String, Word>()

    fun get(word: String, fontPx: Float, density: Density): Word = words.getOrPut(word) {
        val renderScale = 0.5f
        val sizePx = fontPx * renderScale
        val style = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (sizePx / density.density / density.fontScale).sp,
            letterSpacing = (-0.03).em,
            color = Color.White,
        )
        val layout = measurer.measure(word, style, softWrap = false, maxLines = 1, overflow = TextOverflow.Visible)
        val w = layout.size.width.coerceAtLeast(1)
        val h = layout.size.height.coerceAtLeast(1)
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawText(layout)
        }
        // Inter's cap height is ~0.727 em: centre the capitals, not the line box, on the row
        val capCenter = layout.firstBaseline - 0.727f * sizePx / 2f
        Word(
            image = image,
            tileWidth = (w + sizePx * 0.22f) / renderScale,
            drawSize = IntSize((w / renderScale).roundToInt(), (h / renderScale).roundToInt()),
            capCenterY = capCenter / renderScale,
        )
    }
}

/**
 * Draws a [GiantTextState] across the whole of its bounds. [centerY] puts the plane's centre
 * somewhere other than the middle - the page header pins it to the middle of the *screen*, so the
 * column it shows is the very one the full-screen story continues.
 */
fun Modifier.giantText(
    state: GiantTextState,
    time: RecapTime,
    measurer: TextMeasurer,
    centerY: (DrawScope.() -> Float)? = null,
): Modifier = drawWithCache {
    val cache = GiantWordCache(measurer)
    val fontPx = GiantFontSize.toPx()
    val capHeight = 0.727f * fontPx
    val pitch = capHeight * 1.16f
    val baseSpeed = 22.dp.toPx()
    var lastColor = Color.Unspecified
    var filter: ColorFilter? = null
    onDrawBehind {
        val planeAlpha = state.alpha.value
        if (planeAlpha <= 0.003f) return@onDrawBehind
        val color = state.color()
        if (color != lastColor) {
            lastColor = color
            filter = ColorFilter.tint(color, BlendMode.SrcIn)
        }
        val span = hypot(size.width, size.height) * 1.1f / state.scale.value.coerceAtLeast(0.2f)
        val seconds = time.seconds
        withTransform({
            translate(state.centerX.value * size.width, (centerY?.invoke(this@onDrawBehind) ?: (size.height / 2f)) + state.offsetY.value)
            rotate(state.rotation.value, pivot = Offset.Zero)
            scale(state.scale.value, state.scale.value, pivot = Offset.Zero)
        }) {
            state.rows.forEachIndexed { i, row ->
                val fill = row.fill.value
                if (fill <= 0.002f) return@forEachIndexed
                val index = i - state.half
                val word = cache.get(row.word, fontPx, this)
                val direction = if (index % 2 == 0) -1.0 else 1.0
                val speed = baseSpeed * (1.0 + 0.18 * ((index + 8) % 3))
                val phase = ((seconds * speed) % word.tileWidth) * direction
                val slide = (1f - easeOutCubic(fill)) * span * row.side
                val base = phase.toFloat() + slide
                val tiles = ceil((base + span / 2f) / word.tileWidth)
                var x = base - tiles * word.tileWidth
                val top = index * pitch - word.capCenterY
                while (x < span / 2f) {
                    drawImage(
                        image = word.image,
                        dstOffset = IntOffset(x.roundToInt(), top.roundToInt()),
                        dstSize = word.drawSize,
                        alpha = planeAlpha * fill,
                        colorFilter = filter,
                    )
                    x += word.tileWidth
                }
            }
        }
    }
}

/** Brightened copies of a palette for text: the mesh tones themselves are too dark to read on it. */
fun RecapPalette.textColors(): List<Color> = listOf(c1, c3, c0, c2).map { c ->
    val hsl = FloatArray(3)
    colorToHSL(c.toArgb(), hsl)
    hsl[1] = (hsl[1] + 0.2f).coerceAtMost(0.95f)
    hsl[2] = 0.78f
    Color(hslToColor(hsl))
}

