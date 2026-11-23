// DesktopMusicApp.kt
// The desktop content behind the window: the shared shell frame, the desktop navigation graph and the
// mini player. runDesktopApp owns the window and playback; this is only what is drawn inside it.

package com.mochyy.mochymusic.shared.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.datastore.preferences.core.emptyPreferences
import androidx.navigation.compose.rememberNavController
import com.mochyy.mochymusic.constants.DefaultOpenTabKey
import com.mochyy.mochymusic.shared.desktop.navigation.DesktopNavHost
import com.mochyy.mochymusic.shared.desktop.navigation.DesktopRoutes
import com.mochyy.mochymusic.shared.desktop.ui.DesktopShell
import com.mochyy.mochymusic.shared.library.LocalLibrary
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider
import com.mochyy.mochymusic.shared.media.SearchSource
import com.mochyy.mochymusic.shared.playback.LocalPlayerConnection
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.mochyy.mochymusic.shared.settings.PlaybackPreferences
import com.mochyy.mochymusic.shared.ui.player.MiniPlayerBar
import com.mochyy.mochymusic.shared.ui.search.SearchState
import com.mochyy.mochymusic.shared.ui.settings.codecBadgePrefs
import com.mochyy.mochymusic.utils.rememberPreference

@Composable
internal fun DesktopMusicApp(
    source: SearchSource,
    lyricsProviders: List<LyricsProvider>,
    settings: PlaybackPreferences,
    library: LocalLibrary,
    queue: SharedQueue,
    commands: DesktopShellCommands,
    modifier: Modifier = Modifier,
) {
    // the one seam shared screens use; runDesktopApp provides the desktop player above this
    val player = LocalPlayerConnection.current ?: return
    val playback by player.state.collectAsState()
    val preferences by settings.values.collectAsState(initial = emptyPreferences())
    val codecPrefs = codecBadgePrefs(preferences)
    // the query and results live above the navigation graph, so switching tabs doesn't lose them
    val scope = rememberCoroutineScope()
    val searchState = remember(source, scope) { SearchState(source, scope) }
    val navController = rememberNavController()
    // Settings > Appearance > Default start tab is saved as NavigationTab's enum name; the desktop opens
    // the same tab, and an unknown or missing save opens Home, the way Android's toEnum() falls back.
    // Read once: the graph's start is fixed for the run, so changing the setting doesn't reset the back stack
    val (startTab) = rememberPreference(DefaultOpenTabKey, defaultValue = "HOME")
    val startRoute = remember {
        when (startTab) {
            "SEARCH" -> DesktopRoutes.Search
            "LIBRARY" -> DesktopRoutes.Library
            else -> DesktopRoutes.Home
        }
    }
    // the window shell (menu bar, tray, keyboard) asks the page graph to open things through here
    LaunchedEffect(navController) {
        commands.openSettings = {
            navController.navigate(DesktopRoutes.Settings) { launchSingleTop = true }
        }
        commands.openNowPlaying = {
            navController.navigate(DesktopRoutes.NowPlaying) { launchSingleTop = true }
        }
    }
    DisposableEffect(navController) {
        onDispose {
            commands.openSettings = {}
            commands.openNowPlaying = {}
        }
    }

    DesktopShell(
        navController = navController,
        modifier = modifier,
        bottomBar = {
            MiniPlayerBar(
                state = playback,
                onTogglePlay = player::togglePlayPause,
                onSeek = player::seekTo,
                onOpen = {
                    navController.navigate(DesktopRoutes.NowPlaying) { launchSingleTop = true }
                },
                showFormatBadge = codecPrefs.showBadge,
                showSampleRate = codecPrefs.showSampleRate,
            )
        },
    ) {
        DesktopNavHost(
            navController = navController,
            startRoute = startRoute,
            searchState = searchState,
            library = library,
            queue = queue,
            providers = lyricsProviders,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
