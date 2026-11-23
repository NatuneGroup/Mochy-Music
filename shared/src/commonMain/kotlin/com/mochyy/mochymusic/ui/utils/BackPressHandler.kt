package com.mochyy.mochymusic.ui.utils

import androidx.compose.runtime.Composable

/** Catches the system back gesture while [enabled]: Android's activity BackHandler there, Compose's own elsewhere. */
@Composable
expect fun BackPressHandler(enabled: Boolean = true, onBack: () -> Unit)
