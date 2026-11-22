// DesktopApp.kt
// The desktop entry point the launcher (:desktopApp) hands off to. Windows and start-up live here, in
// the library, so the launcher only owns main() and packaging.

package com.mochyy.mochymusic.shared.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mochyy.mochymusic.shared.desktop.media.DesktopMediaControls
import com.mochyy.mochymusic.shared.desktop.media.YouTubeSearchSource
import com.mochyy.mochymusic.shared.desktop.media.createSystemMediaSession
import com.mochyy.mochymusic.shared.desktop.playback.DesktopPlayerController
import com.mochyy.mochymusic.shared.lyrics.playerLyricsProviders
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.app_name
import com.mochyy.mochymusic.shared.resources.playback_failed
import com.mochyy.mochymusic.shared.ui.shell.StandaloneMusicApp
import com.mochyy.mochymusic.shared.ui.theme.StandaloneMochyTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import org.jetbrains.compose.resources.stringResource

/** Opens Mochy's main window with [font] as the app's typeface (the launcher loads Inter for it). */
fun runDesktopApp(font: FontFamily) = application {
    val search = remember { YouTubeSearchSource() }
    val playbackFailed = stringResource(Res.string.playback_failed)
    val player = remember(playbackFailed) { DesktopPlayerController(playbackFailedText = { playbackFailed }) }
    val http = remember {
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 8_000 }
        }
    }
    val providers = remember(http) { playerLyricsProviders(http) }
    DisposableEffect(player) { onDispose { player.release() } }
    // OS media keys and widgets: started with the player, closed when the app exits.
    val mediaControls = remember(player) { DesktopMediaControls(player) }
    DisposableEffect(player, mediaControls) {
        val session = createSystemMediaSession(player, mediaControls)
        onDispose {
            session.close()
            mediaControls.close()
        }
    }
    DisposableEffect(http) { onDispose { http.close() } }

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        StandaloneMochyTheme(font, desktopPlaybackPreferences) {
            StandaloneMusicApp(search, player, providers, desktopPlaybackPreferences, desktopLibrary)
        }
    }
}
