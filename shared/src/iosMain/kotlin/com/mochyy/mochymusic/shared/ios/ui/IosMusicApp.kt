// IosMusicApp.kt
// The iOS app's frame: MochyShell's tabs (the bar on a phone, the rail on an iPad), the shared
// Search screen, the saved and recent lists, the Settings tab the host hands in, the shared mini
// player, and the player sheet that opens from it.
//
// StandaloneMusicApp is the same frame with the player sheet's lyrics and queue kept inside it; iOS
// outgrew it because its Settings tab is a whole navigation graph (part 3) and its player opens as a
// sheet over every tab. The shared pieces are the same files: MochyShell, SearchScreen, MiniPlayerBar,
// SongRow, findPlayerLyrics and the codec badge settings.

package com.mochyy.mochymusic.shared.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mochyy.mochymusic.constants.DefaultOpenTabKey
import com.mochyy.mochymusic.shared.library.LocalLibrary
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider
import com.mochyy.mochymusic.shared.lyrics.suspendRunCatching
import com.mochyy.mochymusic.shared.media.SearchSource
import com.mochyy.mochymusic.shared.playback.LocalPlayerConnection
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.library_empty
import com.mochyy.mochymusic.shared.resources.library_filter_hint
import com.mochyy.mochymusic.shared.resources.library_filter_no_results
import com.mochyy.mochymusic.shared.resources.library_remove
import com.mochyy.mochymusic.shared.resources.library_update_failed
import com.mochyy.mochymusic.shared.resources.recently_played
import com.mochyy.mochymusic.shared.resources.recently_played_empty
import com.mochyy.mochymusic.shared.resources.shell_credit
import com.mochyy.mochymusic.shared.resources.stats
import com.mochyy.mochymusic.shared.resources.your_library
import com.mochyy.mochymusic.shared.settings.PlaybackPreferences
import com.mochyy.mochymusic.shared.ui.player.MiniPlayerBar
import com.mochyy.mochymusic.shared.ui.search.SearchScreen
import com.mochyy.mochymusic.shared.ui.search.SearchState
import com.mochyy.mochymusic.shared.ui.search.SongRow
import com.mochyy.mochymusic.shared.ui.settings.CodecBadgeWriter
import com.mochyy.mochymusic.shared.ui.settings.SharedSettingsScreen
import com.mochyy.mochymusic.shared.ui.settings.codecBadgePrefs
import com.mochyy.mochymusic.shared.ui.shell.MochyShell
import com.mochyy.mochymusic.shared.ui.shell.ShellTab
import com.mochyy.mochymusic.shared.resources.play
import com.mochyy.mochymusic.ui.screens.recap.StatsScreen
import com.music.innertube.models.SongItem
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IosMusicApp(
    source: SearchSource,
    lyricsProviders: List<LyricsProvider>,
    settings: PlaybackPreferences,
    library: LocalLibrary,
    queue: SharedQueue,
    modifier: Modifier = Modifier,
    // the Settings tab: part 3 hands in the whole shared settings graph; until then the one page
    settingsContent: @Composable (PlaybackPreferences, CodecBadgeWriter) -> Unit =
        { preferences, badgeWriter -> SharedSettingsScreen(preferences, badgeWriter) },
) {
    // the one seam shared screens use; the iOS root provides it above this shell
    val player = LocalPlayerConnection.current ?: return
    val playback by player.state.collectAsState()
    val queued by queue.upcoming.collectAsState()
    val queueReady by queue.ready.collectAsState()
    val queueStorageUnavailable by queue.storageUnavailable.collectAsState()
    val queueWriteFailed by queue.writeFailed.collectAsState()
    val preferences by settings.values.collectAsState(initial = emptyPreferences())
    val codecPrefs = codecBadgePrefs(preferences)
    val scope = rememberCoroutineScope()
    // built above the tabs, so a search keeps running (and its results survive) across tab switches
    val searchState = remember(source, scope) { SearchState(source, scope) }
    val codecBadge = remember(settings, scope) { CodecBadgeWriter(settings, scope) }
    val saved by library.saved.collectAsState(initial = emptyList())
    val recent by library.recent.collectAsState(initial = emptyList())
    var showPlayer by remember { mutableStateOf(false) }
    var libraryUpdateFailed by remember { mutableStateOf(false) }
    // Settings > Appearance > Default start tab, the same key Android's NavigationTab writes
    val (startTab) = rememberPreference(DefaultOpenTabKey, defaultValue = "HOME")

    fun setSaved(song: SongItem, save: Boolean) {
        scope.launch {
            libraryUpdateFailed = suspendRunCatching { library.setSaved(song, save) }.isFailure
        }
    }

    MochyShell(
        modifier = modifier,
        initialTab = shellTabOf(startTab),
        bottomBar = {
            MiniPlayerBar(
                state = playback,
                onTogglePlay = player::togglePlayPause,
                onSeek = player::seekTo,
                onOpen = { showPlayer = true },
                showFormatBadge = codecPrefs.showBadge,
                showSampleRate = codecPrefs.showSampleRate,
            )
        },
    ) { tab ->
        when (tab) {
            ShellTab.Search -> SearchScreen(
                state = searchState,
                onPlay = queue::play,
                queueEnabled = queueReady,
                onQueue = { song ->
                    queue.add(song)
                    showPlayer = true
                },
            )
            ShellTab.Home -> IosHomeTab(
                recent = recent,
                onPlay = queue::play,
                onPlayAll = { songs -> playAll(songs, queue) },
            )
            ShellTab.Library -> IosSongsPage(
                title = stringResource(Res.string.your_library),
                emptyMessage = stringResource(Res.string.library_empty),
                songs = saved,
                updateFailed = libraryUpdateFailed,
                onPlay = queue::play,
                onPlayAll = { playAll(saved, queue) },
                onRemove = { setSaved(it, false) },
            )
            ShellTab.Settings -> settingsContent(settings, codecBadge)
        }
        true
    }

    if (showPlayer) {
        ModalBottomSheet(onDismissRequest = { showPlayer = false }) {
            IosPlayerSheet(
                state = playback,
                upcoming = queued,
                queueReady = queueReady,
                queueStorageUnavailable = queueStorageUnavailable,
                queueWriteFailed = queueWriteFailed,
                providers = lyricsProviders,
                showFormatBadge = codecPrefs.showBadge,
                showSampleRate = codecPrefs.showSampleRate,
                isSaved = saved.any { it.id == playback.song?.id },
                onSave = { song, save -> setSaved(song, save) },
                onAddToQueue = { song -> queue.add(song) },
                onTogglePlay = player::togglePlayPause,
                onSeek = player::seekTo,
                onNext = queue::playNext,
                onPrevious = queue::playPrevious,
                onPlayUpcoming = queue::play,
                onRemoveUpcoming = queue::remove,
            )
        }
    }
}

/** Plays a list from its first song, with the rest behind it in Up next, as Android's play-all does. */
private fun playAll(songs: List<SongItem>, queue: SharedQueue) {
    val first = songs.firstOrNull() ?: return
    queue.replace(songs.drop(1))
    queue.play(first)
}

/**
 * The Home tab: the recently played list, and the recap it can open. The recap lives in a small
 * navigation graph of its own, so its back gesture returns to the list instead of leaving the tab.
 */
@Composable
private fun IosHomeTab(
    recent: List<SongItem>,
    onPlay: (SongItem) -> Unit,
    onPlayAll: (List<SongItem>) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoutes.Home, modifier = Modifier.fillMaxSize()) {
        composable(HomeRoutes.Home) {
            IosSongsPage(
                title = stringResource(Res.string.recently_played),
                emptyMessage = stringResource(Res.string.recently_played_empty),
                songs = recent,
                credit = stringResource(Res.string.shell_credit),
                onPlay = onPlay,
                onPlayAll = onPlayAll,
                onOpenStats = { navController.navigate(HomeRoutes.Stats) },
            )
        }
        composable(HomeRoutes.Stats) {
            StatsScreen(navController = navController)
        }
    }
}

private object HomeRoutes {
    const val Home = "home"
    const val Stats = "stats"
}

/** The tab to open with; an unknown value (an old build's) opens Home, as the enum default did. */
private fun shellTabOf(saved: String): ShellTab = when (saved) {
    "SEARCH" -> ShellTab.Search
    "LIBRARY" -> ShellTab.Library
    "SETTINGS" -> ShellTab.Settings
    else -> ShellTab.Home
}

/**
 * The saved or recently played songs, with the filter the shared standalone list has. A tap plays the
 * song; the row that owns a library entry also offers removing it.
 */
@Composable
internal fun IosSongsPage(
    title: String,
    emptyMessage: String,
    songs: List<SongItem>,
    onPlay: (SongItem) -> Unit,
    onRemove: ((SongItem) -> Unit)? = null,
    credit: String? = null,
    updateFailed: Boolean = false,
    onPlayAll: ((List<SongItem>) -> Unit)? = null,
    onOpenStats: (() -> Unit)? = null,
) {
    var filter by remember(title) { mutableStateOf("") }
    val visible = remember(songs, filter) {
        val query = filter.trim()
        if (query.isEmpty()) songs else songs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
                song.artists.any { artist -> artist.name.contains(query, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.weight(1f).padding(vertical = 24.dp),
                )
                if (onOpenStats != null) {
                    // the recap is one tap from the list
                    TextButton(onClick = onOpenStats) {
                        Text(stringResource(Res.string.stats))
                    }
                }
                if (onPlayAll != null && visible.isNotEmpty()) {
                    TextButton(onClick = { onPlayAll(visible) }) {
                        Text(stringResource(Res.string.play))
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (filter.isEmpty()) {
                            Text(
                                stringResource(Res.string.library_filter_hint),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    },
                )
            }
        }
        if (updateFailed) item {
            Text(stringResource(Res.string.library_update_failed), color = MaterialTheme.colorScheme.error)
        }
        if (visible.isEmpty()) item {
            Text(
                if (filter.isBlank()) emptyMessage else stringResource(Res.string.library_filter_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(visible, key = { it.id }) { song ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SongRow(song = song, onClick = { onPlay(song) }, modifier = Modifier.weight(1f))
                if (onRemove != null) {
                    TextButton(onClick = { onRemove(song) }) {
                        Text(stringResource(Res.string.library_remove), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (credit != null) item {
            Text(
                credit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
}
