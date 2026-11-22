package com.mochyy.mochymusic.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.datastore.preferences.core.emptyPreferences
import com.mochyy.mochymusic.constants.AccentColorKey
import com.mochyy.mochymusic.constants.PureBlackKey
import com.mochyy.mochymusic.shared.settings.PlaybackPreferences

@Composable
internal fun StandaloneMochyTheme(
    font: FontFamily,
    settings: PlaybackPreferences,
    content: @Composable () -> Unit,
) {
    val preferences by settings.values.collectAsState(initial = emptyPreferences())
    val accent = preferences[AccentColorKey]?.takeIf { it != 0 }?.let { Color(it) }
    MochySharedTheme(font, pureBlack = preferences[PureBlackKey] ?: false, accent = accent, content = content)
}
