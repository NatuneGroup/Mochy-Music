// ShareRegions.kt
//
// Where the Cinematic editor's outlines sit on the picture. The picture itself (ShareCanvas) still
// lives in :app; this holder moves here so the outlines in ShareEditOutlines can be shared too.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * The Cinematic editor outlines the song and the words; these are where they sit on the picture,
 * in its own coordinates, updated as they move. Null until laid out.
 */
@Stable
class ShareRegions {
    var header by mutableStateOf<Rect?>(null)
    var words by mutableStateOf<Rect?>(null)
}
