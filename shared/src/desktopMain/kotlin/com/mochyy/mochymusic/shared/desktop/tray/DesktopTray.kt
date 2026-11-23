// DesktopTray.kt
// Mochy in the system tray: the window's play/pause, previous and next, a way back to the window, and
// the quit that the close button no longer does. The icon carries the song the mini player shows, so a
// closed window still says what is playing when the platform shows tooltips.

package com.mochyy.mochymusic.shared.desktop.tray

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import com.mochyy.mochymusic.shared.playback.PlayerState
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.desktop_hide_window
import com.mochyy.mochymusic.shared.resources.desktop_quit
import com.mochyy.mochymusic.shared.resources.desktop_show_window
import com.mochyy.mochymusic.shared.resources.desktop_tray_tooltip
import com.mochyy.mochymusic.shared.resources.next
import com.mochyy.mochymusic.shared.resources.nav_mochy_filled
import com.mochyy.mochymusic.shared.resources.pause
import com.mochyy.mochymusic.shared.resources.play
import com.mochyy.mochymusic.shared.resources.previous
import com.mochyy.mochymusic.shared.resources.settings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Everything the tray can do; the window owns the callbacks, the tray only draws them. */
internal class DesktopTrayActions(
    val showWindow: () -> Unit,
    val hideWindow: () -> Unit,
    val togglePlayPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val openSettings: () -> Unit,
    val quit: () -> Unit,
)

@Composable
internal fun ApplicationScope.DesktopTray(
    playback: PlayerState,
    windowVisible: Boolean,
    actions: DesktopTrayActions,
) {
    val song = playback.song
    // the platform's tray tooltip is a plain line; the song goes first when one is playing
    val tooltip = when {
        song == null -> stringResource(Res.string.desktop_tray_tooltip)
        else -> "${stringResource(Res.string.desktop_tray_tooltip)} · ${song.title} — ${song.artists.joinToString(", ") { it.name }}"
    }
    Tray(
        icon = painterResource(Res.drawable.nav_mochy_filled),
        tooltip = tooltip,
        // a click on the icon brings the window back, as the phone's launcher icon would
        onAction = { if (windowVisible) actions.hideWindow() else actions.showWindow() },
        menu = {
            Item(
                text = stringResource(if (windowVisible) Res.string.desktop_hide_window else Res.string.desktop_show_window),
                onClick = { if (windowVisible) actions.hideWindow() else actions.showWindow() },
            )
            Separator()
            Item(
                text = stringResource(if (playback.isPlaying) Res.string.pause else Res.string.play),
                enabled = song != null,
                onClick = actions.togglePlayPause,
            )
            Item(
                text = stringResource(Res.string.previous),
                enabled = song != null,
                onClick = actions.previous,
            )
            Item(
                text = stringResource(Res.string.next),
                enabled = song != null,
                onClick = actions.next,
            )
            Separator()
            Item(text = stringResource(Res.string.settings), onClick = actions.openSettings)
            Separator()
            Item(text = stringResource(Res.string.desktop_quit), onClick = actions.quit)
        },
    )
}
