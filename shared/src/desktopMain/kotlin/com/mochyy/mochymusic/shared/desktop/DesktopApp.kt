// DesktopApp.kt
// The desktop entry point the launcher (:desktopApp) hands off to. Windows and start-up live here, in
// the library, so the launcher only owns main() and packaging.

package com.mochyy.mochymusic.shared.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mochyy.mochymusic.shared.desktop.media.YouTubeSearchSource
import com.mochyy.mochymusic.shared.desktop.playback.DesktopPlayerController
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.app_name
import com.mochyy.mochymusic.shared.resources.playback_failed
import com.mochyy.mochymusic.shared.ui.player.MiniPlayerBar
import com.mochyy.mochymusic.shared.ui.search.SearchScreen
import com.mochyy.mochymusic.shared.ui.shell.MochyShell
import com.mochyy.mochymusic.shared.ui.shell.ShellTab
import com.mochyy.mochymusic.shared.ui.theme.MochySharedTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Opens Mochy's main window with [font] as the app's typeface (the launcher loads Inter for it). */
fun runDesktopApp(font: FontFamily) = application {
    val search = remember { YouTubeSearchSource() }
    // the failure line is read when a song fails, on the player's thread, so it follows the current language
    val player = remember { DesktopPlayerController(playbackFailedText = { runBlocking { getString(Res.string.playback_failed) } }) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        MochySharedTheme(font = font) {
            val state by player.state.collectAsState()
            MochyShell(
                bottomBar = { MiniPlayerBar(state, onTogglePlay = player::togglePlayPause, onSeek = player::seekTo) },
            ) { tab ->
                when (tab) {
                    ShellTab.Search -> {
                        SearchScreen(source = search, onPlay = player::play)
                        true
                    }
                    else -> false
                }
            }
        }
    }
}
