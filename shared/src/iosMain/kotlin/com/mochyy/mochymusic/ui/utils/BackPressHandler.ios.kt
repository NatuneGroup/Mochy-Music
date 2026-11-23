// BackPressHandler.ios.kt
// The shared screens' back action on iOS: Compose Multiplatform's own BackHandler, which the system
// gestures (swipe from the edge in a UINavigationController, or the back button on a keyboard) feed.
// Before CMP had it there was nothing to connect, so the old actual did nothing.

package com.mochyy.mochymusic.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun BackPressHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
