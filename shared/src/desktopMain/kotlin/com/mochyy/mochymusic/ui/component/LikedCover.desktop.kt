package com.mochyy.mochymusic.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal actual fun DrawScope.drawLikedCover(corners: List<Color>) = drawLikedCoverSkia(corners)
