// Type.kt

package com.mochyy.mochymusic.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.shared.ui.theme.mochyTypography

private val Inter = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** a look module's font, standing in for inter across the app while that look is on; null is inter */
object LookFont {
    var family: FontFamily? by mutableStateOf(null)
}

/** the app's own font choice: mochy's Inter, or the phone's system font. a look's font still wins over both */
object AppFontChoice {
    var useSystem by mutableStateOf(false)
}

// read through a state, so text set in it follows a look's font, or the choice, turning on or off
val InterFontFamily: FontFamily
    get() = LookFont.family ?: if (AppFontChoice.useSystem) FontFamily.Default else Inter

/** the app's text styles in [font], rebuilt when a look's font comes or goes; shared with desktop and iOS */
fun appTypography(font: FontFamily) = mochyTypography(font)
