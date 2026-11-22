// Theme.kt

package com.mochyy.mochymusic.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score
import com.mochyy.mochymusic.shared.ui.theme.ForceDarkTheme as SharedForceDarkTheme
import com.mochyy.mochymusic.shared.ui.theme.mochyColorScheme
import com.mochyy.mochymusic.shared.ui.theme.pureBlack as sharedPureBlack

val DefaultThemeColor = Color(0xFF8E8E8E)

/** mochy renders dark-only; the switch and the palette live in :shared so desktop and iOS match. */
const val ForceDarkTheme = SharedForceDarkTheme

@Composable
fun MochyTheme(
    darkTheme: Boolean = ForceDarkTheme,
    pureBlack: Boolean = false,
    /** the chrome's accent: replaces primary and secondary, the background stays as it is */
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Dynamic (Material You) colour is deliberately not used - the app ships its own palette.
    // The branch is gone rather than dead-but-present: dynamic*ColorScheme is API 31.
    val baseColorScheme = mochyColorScheme(darkTheme)

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, accent) {
        val pured = if (darkTheme && pureBlack) baseColorScheme.sharedPureBlack(true) else baseColorScheme
        if (accent == null) pured else pured.withAccent(accent)
    }

    val font = InterFontFamily
    val typography = remember(font) { appTypography(font) }
    CompositionLocalProvider(LocalChromeAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
        ) {
            ProvideTextStyle(
                value = typography.bodyLarge,
                content = content
            )
        }
    }
}

/** the accent the chrome is using, or null for mochy's own grey; controls that take the accent read it */
val LocalChromeAccent = compositionLocalOf<Color?> { null }

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) = sharedPureBlack(apply)

/** the accent as primary and secondary; the text on it is black or white, whichever reads better */
fun ColorScheme.withAccent(accent: Color): ColorScheme {
    val onAccent = if (accent.luminance() > 0.4f) Color.Black else Color.White
    return copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
        onSecondary = onAccent,
    )
}

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
