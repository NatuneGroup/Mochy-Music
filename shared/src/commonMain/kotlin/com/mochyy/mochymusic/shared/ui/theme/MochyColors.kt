// MochyColors.kt
// Mochy's own palette (from Mochy Music by realidkroo); dynamic Material You colour is deliberately not
// used. Shared so every platform's app paints the same greys.

package com.mochyy.mochymusic.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * mochy renders dark-only. The light palette was never finished - large parts of the UI hardcode white
 * text and dark glass - so it is pinned as one switch instead of being decided at every call site.
 */
const val ForceDarkTheme = true

/** Mochy's base scheme; the light one exists but the app never turns it on, see [ForceDarkTheme]. */
fun mochyColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFE0E0E0),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF333333),
            onPrimaryContainer = Color.White,
            secondary = Color(0xFFB0B0B0),
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF2C2C2C),
            onSecondaryContainer = Color.White,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF1A1A1A),
            onSurfaceVariant = Color.White,
            surfaceTint = Color.Black
        )
    } else {
        lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E0E0),
            onPrimaryContainer = Color.Black,
            secondary = Color(0xFF333333),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFCCCCCC),
            onSecondaryContainer = Color.Black,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color.Black,
            surfaceTint = Color.White
        )
    }

fun ColorScheme.pureBlack(apply: Boolean): ColorScheme =
    if (apply) copy(surface = Color.Black, background = Color.Black) else this

/**
 * The theme for platforms without the Android app's extras (desktop, iOS): Mochy's palette and type in
 * [font]. The Android app keeps its own MochyTheme wrapper, built on the same two functions.
 */
@Composable
fun MochySharedTheme(
    font: FontFamily,
    darkTheme: Boolean = ForceDarkTheme,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(darkTheme, pureBlack) {
        mochyColorScheme(darkTheme).let { if (darkTheme) it.pureBlack(pureBlack) else it }
    }
    val typography = remember(font) { mochyTypography(font) }
    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        ProvideTextStyle(value = typography.bodyLarge, content = content)
    }
}
