// RecapStoryPlatform.kt
//
// What the monthly story needs from the platform it runs on: picture loads and palettes, the moving
// mesh behind the words, the listener's ID card (its front and back, with the device tilt), and the
// small player for the two song snippets. The story and its script are shared; each platform
// provides these. Android's implementation is app's RecapStoryHost.kt.

package com.mochyy.mochymusic.ui.screens.recap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope

interface RecapStoryPlatform {
    /** Asks the picture loader to fetch [url] before the story shows it. */
    fun prefetch(url: String)

    /** The palette of a picture, or the fallback palette when there's no picture to read. */
    suspend fun paletteOf(url: String?): RecapPalette

    /** A snippet player for the story, driven by [scope] and released when the story goes. */
    fun snippets(scope: CoroutineScope): RecapSnippets

    /** The slow mesh behind the words, drawn from [palette] at [alpha]. */
    @Composable
    fun Mesh(palette: () -> RecapPalette, time: RecapTime, alpha: () -> Float, modifier: Modifier)

    /** The listener's ID card, its front or its back as [state] says, [cardWidth] px wide. */
    @Composable
    fun CardFace(state: StoryState, time: RecapTime, cardWidth: Float)
}

/** The two short clips the story plays. They use their own player, so the real queue is never touched. */
interface RecapSnippets {
    /** Starts [songId] from its beginning, fading in over [fadeInMs]. */
    fun play(songId: String, fadeInMs: Long = 1400L)

    /** Fades whatever is playing out over [fadeOutMs]. */
    fun stop(fadeOutMs: Long = 900L)

    /** Stops for good; nothing plays after this. */
    fun release()
}
