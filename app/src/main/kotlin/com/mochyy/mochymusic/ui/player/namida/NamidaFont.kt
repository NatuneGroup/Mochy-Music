// namida player style — the typeface and the palette the style is drawn with.
//
// The style is self-contained on purpose: it brings its own font (Lexend, the typeface namida
// uses, under its own SIL Open Font License from Google Fonts), its own colours derived from the
// artwork, its own lyrics view and its own queue page, so it reads the same whatever the rest of
// the player does.

@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.mochyy.mochymusic.ui.player.namida

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.mochyy.mochymusic.R

/** namida's typeface: one variable file covering every weight the player reads. */
val NamidaFontFamily: FontFamily = FontFamily(
    Font(
        R.font.lexend_variable,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(FontVariation.weight(300)),
    ),
    Font(R.font.lexend_variable, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(
        R.font.lexend_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.lexend_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.lexend_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * The player's palette, built from the cover's colour the way namida builds its themes: the
 * colour itself as the accent, quiet mixes of it for the panel, and inks that sit on top.
 */
class NamidaPalette(
    /** the cover's colour, namida's `miniplayerColor`, at namida's dark-theme opacity */
    val accent: Color,
    /** the panel the player is drawn on */
    val surface: Color,
    /** a quiet tint of the accent, for the pill behind the top bar's more button */
    val secondaryContainer: Color,
    /** the main ink and its softer readings */
    val onSurface: Color,
    val onSurfaceSoft: Color,
    /** icons in the top bar and the small numbers */
    val onSecondaryContainer: Color,
)

/** namida's dark theme colour derivations, from the artwork's seed colour. */
fun namidaPalette(seed: Color): NamidaPalette {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(seed.toArgb(), hsl)
    // namida keeps the hue, quiets the saturation and lifts the lightness for its accent
    val accentHsl = floatArrayOf(hsl[0], (hsl[1] * 0.9f).coerceIn(0.05f, 0.85f), 0.62f)
    val accent = Color(androidx.core.graphics.ColorUtils.HSLToColor(accentHsl)).copy(alpha = 0.78f)
    // the panel: the accent's hue held dark and quiet, the way namida's dark surface keeps a
    // trace of the song's colour
    fun surfaceTone(lightness: Float, alpha: Float) =
        Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hsl[0], (hsl[1] * 0.30f).coerceIn(0.02f, 0.20f), lightness)))
            .copy(alpha = alpha)
    return NamidaPalette(
        accent = accent,
        surface = surfaceTone(0.09f, 1f),
        secondaryContainer = surfaceTone(0.30f, 0.20f),
        onSurface = Color(0xFFEBE7F2),
        onSurfaceSoft = Color(0xFFEBE7F2).copy(alpha = 0.62f),
        onSecondaryContainer = Color(0xFFEBE7F2).copy(alpha = 0.92f),
    )
}

/** The palette the style is drawn with, provided by [NamidaPlayerStyle] around its content. */
val LocalNamidaPalette = staticCompositionLocalOf { namidaPalette(Color(0xFF5B4E8A)) }
