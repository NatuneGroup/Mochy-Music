// DesktopMediaControls.kt
// The transport requests the operating system can make (media keys, playerctl, desktop media widgets).
// Play, pause and seek go straight to the player; next and previous belong to the shared queue, so the
// UI registers them through onNext and onPrevious.

package com.mochyy.mochymusic.shared.desktop.media

import com.mochyy.mochymusic.shared.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DesktopMediaControls(private val player: PlayerController) {
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Set by the shared UI to play the next queued song. Null until it is wired; Next then does nothing. */
    @Volatile var onNext: (() -> Unit)? = null

    /** Set by the shared UI to go back a song. Null means Previous restarts the current song instead. */
    @Volatile var onPrevious: (() -> Unit)? = null

    fun playPause() = onMain { player.togglePlayPause() }

    fun play() = onMain { if (!player.state.value.isPlaying) player.togglePlayPause() }

    fun pause() = onMain { if (player.state.value.isPlaying) player.togglePlayPause() }

    fun next() = onMain { onNext?.invoke() }

    fun previous() = onMain {
        val handler = onPrevious
        if (handler != null) handler() else player.seekTo(0L)
    }

    fun seekTo(positionMs: Long) = onMain { player.seekTo(positionMs) }

    fun close() = main.cancel()

    // Calls arrive on D-Bus threads; the player must be driven from the UI thread like every other caller.
    private fun onMain(block: () -> Unit) {
        main.launch { block() }
    }
}
