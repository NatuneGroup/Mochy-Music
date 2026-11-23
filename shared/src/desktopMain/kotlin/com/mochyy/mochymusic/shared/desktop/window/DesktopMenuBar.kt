// DesktopMenuBar.kt
// The menu bar a desktop window is expected to have: playback from the keyboard, the way to Now
// playing and Settings without hunting for a tab, and the show/hide and quit that the tray also offers.
// Nothing here opens a page of its own; every item calls back into the window.

package com.mochyy.mochymusic.shared.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.KeyShortcut
import androidx.compose.ui.window.MenuBar
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.desktop_hide_window
import com.mochyy.mochymusic.shared.resources.desktop_menu_playback
import com.mochyy.mochymusic.shared.resources.desktop_menu_view
import com.mochyy.mochymusic.shared.resources.desktop_menu_window
import com.mochyy.mochymusic.shared.resources.desktop_quit
import com.mochyy.mochymusic.shared.resources.desktop_show_window
import com.mochyy.mochymusic.shared.resources.now_playing
import com.mochyy.mochymusic.shared.resources.pause
import com.mochyy.mochymusic.shared.resources.play
import com.mochyy.mochymusic.shared.resources.previous
import com.mochyy.mochymusic.shared.resources.settings
import com.mochyy.mochymusic.shared.resources.skip_next
import org.jetbrains.compose.resources.stringResource

/** what the menu bar can ask the window to do */
internal class DesktopMenuActions(
    val togglePlayPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val openNowPlaying: () -> Unit,
    val openSettings: () -> Unit,
    val toggleWindow: () -> Unit,
    val quit: () -> Unit,
)

@Composable
internal fun FrameWindowScope.DesktopMenuBar(
    isPlaying: Boolean,
    hasSong: Boolean,
    windowVisible: Boolean,
    actions: DesktopMenuActions,
) {
    // Ctrl on Linux and Windows, ⌘ on macOS, the convention each platform keeps
    val mac = System.getProperty("os.name").orEmpty().lowercase().startsWith("mac")
    fun shortcut(key: Key) = KeyShortcut(key = key, ctrl = !mac, meta = mac)

    MenuBar {
        Menu(stringResource(Res.string.desktop_menu_playback), mnemonic = 'P') {
            Item(
                text = stringResource(if (isPlaying) Res.string.pause else Res.string.play),
                shortcut = shortcut(Key.P),
                enabled = hasSong,
                onClick = actions.togglePlayPause,
            )
            Item(
                text = stringResource(Res.string.skip_next),
                shortcut = shortcut(Key.DirectionRight),
                enabled = hasSong,
                onClick = actions.next,
            )
            Item(
                text = stringResource(Res.string.previous),
                shortcut = shortcut(Key.DirectionLeft),
                enabled = hasSong,
                onClick = actions.previous,
            )
        }
        Menu(stringResource(Res.string.desktop_menu_view), mnemonic = 'V') {
            Item(
                text = stringResource(Res.string.now_playing),
                onClick = actions.openNowPlaying,
            )
            Separator()
            Item(
                text = stringResource(Res.string.settings),
                shortcut = shortcut(Key.S),
                onClick = actions.openSettings,
            )
        }
        Menu(stringResource(Res.string.desktop_menu_window), mnemonic = 'W') {
            Item(
                text = stringResource(if (windowVisible) Res.string.desktop_hide_window else Res.string.desktop_show_window),
                onClick = actions.toggleWindow,
            )
            Separator()
            Item(
                text = stringResource(Res.string.desktop_quit),
                shortcut = shortcut(Key.Q),
                onClick = actions.quit,
            )
        }
    }
}
