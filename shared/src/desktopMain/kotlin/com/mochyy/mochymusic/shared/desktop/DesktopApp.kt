// DesktopApp.kt
// The desktop entry point the launcher (:desktopApp) hands off to. Windows and start-up live here, in
// the library, so the launcher only owns main() and packaging. A second launch never opens a second
// window: it asks the running one to come forward instead. Closing can hide to the tray, the window
// remembers its place, and the menu bar, tray and keyboard all drive the same player the pages do.

package com.mochyy.mochymusic.shared.desktop

import androidx.datastore.preferences.core.emptyPreferences
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mochyy.mochymusic.shared.desktop.media.DesktopMediaControls
import com.mochyy.mochymusic.shared.desktop.media.YouTubeSearchSource
import com.mochyy.mochymusic.shared.desktop.media.createSystemMediaSession
import com.mochyy.mochymusic.shared.desktop.playback.DesktopPlayerController
import com.mochyy.mochymusic.shared.desktop.tray.DesktopTray
import com.mochyy.mochymusic.shared.desktop.tray.DesktopTrayActions
import com.mochyy.mochymusic.shared.desktop.window.DesktopCloseToTrayKey
import com.mochyy.mochymusic.shared.desktop.window.DesktopMenuActions
import com.mochyy.mochymusic.shared.desktop.window.DesktopMenuBar
import com.mochyy.mochymusic.shared.desktop.window.DesktopRememberWindowKey
import com.mochyy.mochymusic.shared.desktop.window.DesktopTrayIconKey
import com.mochyy.mochymusic.shared.desktop.window.DesktopWindowSettings
import com.mochyy.mochymusic.shared.desktop.window.MochySingleInstance
import com.mochyy.mochymusic.shared.desktop.window.NoWindowInsets
import com.mochyy.mochymusic.shared.desktop.window.currentGeometry
import com.mochyy.mochymusic.shared.desktop.window.desktopWindowState
import com.mochyy.mochymusic.shared.desktop.window.readDesktopWindowSettings
import com.mochyy.mochymusic.shared.desktop.window.saveWindowGeometry
import com.mochyy.mochymusic.shared.lyrics.playerLyricsProviders
import com.mochyy.mochymusic.shared.playback.LocalPlayerConnection
import com.mochyy.mochymusic.shared.playback.QueuePlayerConnection
import com.mochyy.mochymusic.shared.playback.QueueTransport
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.app_name
import com.mochyy.mochymusic.shared.resources.nav_mochy_filled
import com.mochyy.mochymusic.shared.resources.playback_failed
import com.mochyy.mochymusic.shared.ui.theme.StandaloneMochyTheme
import com.mochyy.mochymusic.utils.LocalAppPreferences
import com.mochyy.mochymusic.utils.PreferencesCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Opens Mochy's main window with [font] as the app's typeface (the launcher loads Inter for it). */
fun runDesktopApp(font: FontFamily) {
    DesktopPaths.ensure()
    // one window per user: a second launch only asks the first to come forward and leaves
    if (!MochySingleInstance.acquire()) return
    // warm before the first frame, so rememberPreference starts on the saved value
    PreferencesCache.start(desktopPlaybackPreferences.store)
    // covers get the same crossfade, memory share and size setting as Android, with a disk cache
    configureDesktopImageLoader()
    // shared ViewModels get the store above and desktopDatabase; nothing opens either until one asks
    com.mochyy.mochymusic.di.startMochyKoin(database = { desktopDatabase }, preferences = { desktopPlaybackPreferences.store })
    // the app's font pick falls back to this inter (StandaloneMochyTheme hands it over too, as iOS does)
    com.mochyy.mochymusic.ui.theme.AppFont.inter = font
    // where the window was is read before it opens, so it never jumps from a default place to its own
    val windowSettings = runBlocking { readDesktopWindowSettings(desktopPlaybackPreferences.store) }
    runDesktopWindow(font, windowSettings)
}

private fun runDesktopWindow(font: FontFamily, savedWindow: DesktopWindowSettings) = application {
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
    val queueTransport = remember { QueueTransport() }
    val mediaControls = remember(player, queueTransport) { DesktopMediaControls(player, queueTransport) }
    DisposableEffect(player, mediaControls) {
        val session = createSystemMediaSession(player, mediaControls)
        onDispose {
            session.close()
            mediaControls.close()
        }
    }
    DisposableEffect(http) { onDispose { http.close() } }
    // Up next for the whole app, outside the window's UI, like the iOS runtime's
    val queueScope = rememberCoroutineScope()
    val queue = remember(player, queueTransport) {
        SharedQueue(player, desktopPlaybackPreferences, desktopLibrary, queueTransport, queueScope)
    }
    DisposableEffect(queue) {
        queue.start()
        onDispose { queue.close() }
    }
    // the shared screens' player: the same controller and Up next, behind the portable contract
    val connection = remember(player, queue, queueScope) { QueuePlayerConnection(player, queue, queueScope) }

    // the page commands the menu bar, the tray and the keyboard share; DesktopMusicApp fills them in
    val commands = remember { DesktopShellCommands() }
    val windowState = remember(savedWindow) {
        desktopWindowState(savedWindow.geometry.takeIf { savedWindow.rememberWindow })
    }
    // the settings page edits these while the app runs, so they are read live, not only at start-up
    val preferences by desktopPlaybackPreferences.values.collectAsState(initial = emptyPreferences())
    val closeToTray = preferences[DesktopCloseToTrayKey] ?: true
    val trayIcon = preferences[DesktopTrayIconKey] ?: savedWindow.trayIcon
    val rememberWindow = preferences[DesktopRememberWindowKey] ?: savedWindow.rememberWindow
    val playback by player.state.collectAsState()
    // the title bar says what is playing, the way a desktop player is expected to
    val windowTitle = playback.song?.let { "${it.title} — ${stringResource(Res.string.app_name)}" }
        ?: stringResource(Res.string.app_name)
    // starting in the tray means no window until the tray icon asks for one
    var windowVisible by remember { mutableStateOf(!(savedWindow.startMinimized && savedWindow.trayIcon)) }

    fun saveNow() {
        // always written: restoring is what the "remember the window" switch turns off, and a stale
        // remembered copy of the switch would make quitting at the wrong moment lose the place anyway
        runBlocking { saveWindowGeometry(desktopPlaybackPreferences.store, windowState.currentGeometry()) }
    }

    fun quit() {
        saveNow()
        exitApplication()
    }

    // turning the tray icon off while the window is hidden must not leave the app unreachable
    LaunchedEffect(trayIcon) { if (!trayIcon && !windowVisible) windowVisible = true }

    Window(
        onCloseRequest = { if (closeToTray && trayIcon) windowVisible = false else quit() },
        visible = windowVisible,
        title = windowTitle,
        icon = painterResource(Res.drawable.nav_mochy_filled),
        state = windowState,
        onPreviewKeyEvent = { event ->
            desktopShortcut(event, player::togglePlayPause, player::skipToNext, player::skipToPrevious, commands)
        },
    ) {
        // the menu bar is a window frame part, so it sits inside the window, above the page
        DesktopMenuBar(
            isPlaying = playback.isPlaying,
            hasSong = playback.song != null,
            windowVisible = windowVisible,
            actions = remember(commands) {
                DesktopMenuActions(
                    togglePlayPause = player::togglePlayPause,
                    next = player::skipToNext,
                    previous = player::skipToPrevious,
                    openNowPlaying = { commands.openNowPlaying() },
                    openSettings = { commands.openSettings() },
                    toggleWindow = { windowVisible = !windowVisible },
                    quit = ::quit,
                )
            },
        )
        // a second launch (the launcher run twice, a file manager's "open") wakes this window instead.
        // The lock is released by the shutdown hook MochySingleInstance installs, not here: hiding the
        // window must never leave the app running without a lock another launch could take
        DisposableEffect(Unit) {
            MochySingleInstance.onShowRequest {
                windowVisible = true
                windowState.isMinimized = false
                window.toFront()
            }
            onDispose { }
        }
        // the place is saved a moment after the window settles, not on every pixel of a drag
        LaunchedEffect(windowState, rememberWindow) {
            if (!rememberWindow) return@LaunchedEffect
            snapshotFlow { Triple(windowState.position, windowState.size, windowState.placement) }
                .distinctUntilChanged()
                .collectLatest {
                    delay(400)
                    saveWindowGeometry(desktopPlaybackPreferences.store, windowState.currentGeometry())
                }
        }
        CompositionLocalProvider(
            LocalAppPreferences provides desktopPlaybackPreferences.store,
            LocalPlayerConnection provides connection,
            // settings sub-pages: nothing covers the bottom here, and their header is a plain tint
            com.mochyy.mochymusic.LocalPlayerAwareWindowInsets provides NoWindowInsets,
            com.mochyy.mochymusic.ui.component.LocalSubSettingsChrome provides com.mochyy.mochymusic.ui.component.PlainSubSettingsChrome,
        ) {
            StandaloneMochyTheme(font, desktopPlaybackPreferences) {
                DesktopMusicApp(
                    source = search,
                    lyricsProviders = providers,
                    settings = desktopPlaybackPreferences,
                    library = desktopLibrary,
                    queue = queue,
                    commands = commands,
                )
            }
        }
    }

    if (trayIcon) {
        DesktopTray(
            playback = playback,
            windowVisible = windowVisible,
            actions = remember(commands) {
                DesktopTrayActions(
                    showWindow = { windowVisible = true; windowState.isMinimized = false },
                    hideWindow = { windowVisible = false },
                    togglePlayPause = player::togglePlayPause,
                    next = player::skipToNext,
                    previous = player::skipToPrevious,
                    openSettings = { windowVisible = true; commands.openSettings() },
                    quit = ::quit,
                )
            },
        )
    }

}

/**
 * Ctrl/⌘ shortcuts, the ones a window is expected to answer while a page has focus. A bare space or
 * arrow stays with the page: typing in the search field must never pause the player. Returns true
 * when the event was ours, so the page doesn't see it.
 */
private fun desktopShortcut(
    event: KeyEvent,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    commands: DesktopShellCommands,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    if (!command) {
        // the keys keyboards put on the top row; on Linux the OS media session gets these first
        return when (event.key) {
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { onTogglePlayPause(); true }
            Key.MediaNext -> { onNext(); true }
            Key.MediaPrevious -> { onPrevious(); true }
            else -> false
        }
    }
    return when (event.key) {
        Key.P, Key.Spacebar -> { onTogglePlayPause(); true }
        Key.DirectionRight -> { onNext(); true }
        Key.DirectionLeft -> { onPrevious(); true }
        Key.S -> { commands.openSettings(); true }
        else -> false
    }
}

/**
 * What the window shell can ask the page to do. The page graph owns the navigation controller, so it
 * fills these in when it composes; the menu bar, tray and keyboard call them from outside it.
 */
internal class DesktopShellCommands {
    var openSettings: () -> Unit = {}
    var openNowPlaying: () -> Unit = {}
}
