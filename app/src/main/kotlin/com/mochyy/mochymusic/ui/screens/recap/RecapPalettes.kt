// RecapPalettes.kt
//
// The Android side of the recap visuals: the cover and artist palette read with androidx Palette
// (with its LruCache and Coil request), and the AGSL mesh gradient that draws it on Android 13+.
// Everything else the recap draws is shared, in shared's RecapVisuals.kt and RecapStoryDrawing.kt.

package com.mochyy.mochymusic.ui.screens.recap

import android.content.Context
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.ui.component.asAgslPaintShader
import com.mochyy.mochymusic.ui.component.createAgslShader
import com.mochyy.mochymusic.ui.component.setAgslUniform
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
// ---------------------------------------------------------------------------------------------
// Moving gradient from a picture

internal object RecapPalettes {
    private val cache = LruCache<String, RecapPalette>(24)

    suspend fun extract(context: Context, url: String?): RecapPalette {
        if (url.isNullOrBlank()) return RecapPalette.Fallback
        cache.get(url)?.let { return it }
        return try {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(url.resize(120, 120))
                    .size(96, 96)
                    .allowHardware(false)
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap()
                    ?: return@withContext RecapPalette.Fallback
                val palette = withContext(Dispatchers.Default) {
                    val p = Palette.from(bitmap).maximumColorCount(16).generate()
                    val dominant = p.swatches.maxByOrNull { it.population }?.rgb ?: return@withContext RecapPalette.Fallback
                    RecapPalette(
                        c0 = tone(dominant, 0.30f, 0f),
                        c1 = tone(p.vibrantSwatch?.rgb ?: p.lightVibrantSwatch?.rgb ?: dominant, 0.35f, 14f),
                        c2 = tone(p.darkVibrantSwatch?.rgb ?: p.darkMutedSwatch?.rgb ?: dominant, 0.19f, -10f),
                        c3 = tone(p.mutedSwatch?.rgb ?: p.lightMutedSwatch?.rgb ?: dominant, 0.26f, 24f),
                    )
                }
                cache.put(url, palette)
                palette
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RecapPalette.Fallback
        }
    }

    // the swatch's hue at a fixed lightness, saturation capped; [hueShift] keeps two swatches of
    // the same colour from collapsing into one flat tone
    private fun tone(argb: Int, lightness: Float, hueShift: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        val grey = hsl[1] < 0.08f
        hsl[0] = (hsl[0] + if (grey) 0f else hueShift + 360f) % 360f
        hsl[1] = if (grey) hsl[1] else hsl[1].coerceIn(0.32f, 0.68f)
        hsl[2] = lightness
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

/**
 * A slow mesh of the palette's four colours, bent by a gentle warp. Android 13+ draws it as one
 * shader pass (blob centres are worked out here, so the GPU does two sines a pixel); older phones
 * get three soft blobs over the base colour.
 */
@Composable
internal fun MeshGradient(
    palette: () -> RecapPalette,
    time: RecapTime,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) createAgslShader(MeshShaderSource) else null }
    Spacer(
        modifier = modifier.drawWithCache {
            onDrawBehind {
                val a = alpha()
                if (a <= 0.003f) return@onDrawBehind
                val p = palette()
                val t = time.sway * 0.22f
                val aspect = size.height / size.width
                val centers = floatArrayOf(
                    0.15f + 0.30f * sin(t * 0.9f), aspect * (0.18f + 0.14f * cos(t * 0.7f)),
                    0.85f + 0.22f * cos(t * 0.8f + 1.3f), aspect * (0.38f + 0.20f * sin(t * 0.6f + 2.1f)),
                    0.25f + 0.28f * cos(t * 0.55f + 3.2f), aspect * (0.72f + 0.16f * sin(t * 0.85f + 0.7f)),
                    0.80f + 0.20f * sin(t * 0.75f + 4.1f), aspect * (0.94f + 0.10f * cos(t * 0.95f)),
                )
                if (shader != null && Build.VERSION.SDK_INT >= 33) {
                    MeshShader.setUniforms(shader, size.width, size.height, t, centers, p)
                    drawRect(ShaderBrush(shader.asAgslPaintShader()), alpha = a)
                } else {
                    drawMeshFallback(p, centers, a)
                }
            }
        },
    )
}

private fun DrawScope.drawMeshFallback(p: RecapPalette, centers: FloatArray, alpha: Float) {
    drawRect(p.c0, alpha = alpha)
    val blobs = arrayOf(p.c1, p.c2, p.c3)
    val radius = size.width * 0.95f
    for (i in blobs.indices) {
        val c = Offset(centers[(i + 1) * 2] * size.width, centers[(i + 1) * 2 + 1] * size.width)
        drawCircle(
            Brush.radialGradient(listOf(blobs[i], blobs[i].copy(alpha = 0f)), center = c, radius = radius),
            radius = radius,
            center = c,
            alpha = alpha,
        )
    }
}

private object MeshShader {
    @RequiresApi(33)
    fun setUniforms(shader: Any, w: Float, h: Float, t: Float, centers: FloatArray, p: RecapPalette) {
        shader.setAgslUniform("resolution", w, h)
        shader.setAgslUniform("time", t)
        val s = shader as android.graphics.RuntimeShader
        s.setFloatUniform("a", centers[0], centers[1])
        s.setFloatUniform("b", centers[2], centers[3])
        s.setFloatUniform("c", centers[4], centers[5])
        s.setFloatUniform("d", centers[6], centers[7])
        s.setFloatUniform("c0", p.c0.red, p.c0.green, p.c0.blue)
        s.setFloatUniform("c1", p.c1.red, p.c1.green, p.c1.blue)
        s.setFloatUniform("c2", p.c2.red, p.c2.green, p.c2.blue)
        s.setFloatUniform("c3", p.c3.red, p.c3.green, p.c3.blue)
    }
}

private const val MeshShaderSource = """
uniform float2 resolution;
uniform float time;
uniform float2 a;
uniform float2 b;
uniform float2 c;
uniform float2 d;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;

float weight(float2 p, float2 q) {
    float2 v = p - q;
    float w = 1.0 / (0.03 + dot(v, v) * 2.4);
    return w * w;
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / resolution.x;
    p += 0.06 * float2(sin(p.y * 3.3 + time * 2.1), cos(p.x * 2.9 - time * 1.7));
    float wa = weight(p, a);
    float wb = weight(p, b);
    float wc = weight(p, c);
    float wd = weight(p, d);
    float3 col = (c0 * wa + c1 * wb + c2 * wc + c3 * wd) / (wa + wb + wc + wd);
    // a little dither so the dark ramps don't band
    float n = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += (n - 0.5) / 255.0;
    return half4(half3(col), 1.0);
}
"""
