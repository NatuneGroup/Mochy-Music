// DesktopPages.kt
// The pages the desktop graph adds around the shared ones: the shell's Home and Library lists, a
// full-window Now Playing page (the desktop counterpart of the Android bottom sheet), the settings
// landing page built on the shared MochySettingsScreen, and the honest page Android-only rows open.

package com.mochyy.mochymusic.shared.desktop.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.shared.library.LocalLibrary
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider
import com.mochyy.mochymusic.shared.playback.LocalPlayerConnection
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.arrow_back
import com.mochyy.mochymusic.shared.resources.back_button_desc
import com.mochyy.mochymusic.shared.resources.library_empty
import com.mochyy.mochymusic.shared.resources.library_remove
import com.mochyy.mochymusic.shared.resources.library_save
import com.mochyy.mochymusic.shared.resources.library_update_failed
import com.mochyy.mochymusic.shared.resources.lyrics
import com.mochyy.mochymusic.shared.resources.now_playing
import com.mochyy.mochymusic.shared.resources.pause
import com.mochyy.mochymusic.shared.resources.play
import com.mochyy.mochymusic.shared.resources.profile_view_stats
import com.mochyy.mochymusic.shared.resources.queue
import com.mochyy.mochymusic.shared.resources.queue_empty
import com.mochyy.mochymusic.shared.resources.queue_loading
import com.mochyy.mochymusic.shared.resources.recently_played
import com.mochyy.mochymusic.shared.resources.recently_played_empty
import com.mochyy.mochymusic.shared.resources.remove_from_queue
import com.mochyy.mochymusic.shared.resources.rc_stats_title
import com.mochyy.mochymusic.shared.resources.shell_coming_body
import com.mochyy.mochymusic.shared.resources.shell_coming_title
import com.mochyy.mochymusic.shared.resources.shell_credit
import com.mochyy.mochymusic.shared.resources.your_library
import com.mochyy.mochymusic.shared.ui.settings.SettingsCard
import com.mochyy.mochymusic.shared.ui.search.SongRow
import com.mochyy.mochymusic.shared.ui.shell.LibrarySongs
import com.mochyy.mochymusic.shared.ui.shell.LyricsPanel
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.MochySettingsScreen
import com.mochyy.mochymusic.ui.screens.settings.PlainMochySettingsChrome
import com.mochyy.mochymusic.ui.screens.settings.SettingsListPlatform
import com.music.innertube.models.SongItem
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The desktop settings landing page: the shared list, with no Android-only categories and its
 *  desktop pages next to it on the same navigation graph. */
@Composable
internal fun DesktopSettingsHome(navController: NavHostController) {
    MochySettingsScreen(
        navController = navController,
        chrome = PlainMochySettingsChrome,
        platform = DesktopSettingsListPlatform,
        // every row opens beside/instead of the list on the desktop graph, never by the Android route;
        // a route this graph doesn't name lands on the "still in the Android app" page instead of failing
        onOpenPage = { route ->
            val target = if (route in DesktopRoutes.settingsRoutes) route else DesktopRoutes.unavailable(route)
            navController.navigate(target) { launchSingleTop = true }
        },
        // the app's tips card has no desktop twin; this one card opens the stats page the profile
        // header opens on a phone, so recap/stats is reachable from settings as well as the rail
        recommendations = { open ->
            DesktopStatsCard(onClick = { open(DesktopRoutes.Stats) })
        },
        onProfileClick = {
            // already on the settings list: the avatar is not a second copy of it
            navController.popBackStack(DesktopRoutes.Settings, inclusive = false)
        },
    )
}

// the shared list's flags: desktop has no audio lab, gesture editor, module store or device sheet
internal val DesktopSettingsListPlatform = SettingsListPlatform()

@Composable
private fun DesktopStatsCard(onClick: () -> Unit) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.rc_stats_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.profile_view_stats),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DesktopHomePage(library: LocalLibrary, queue: SharedQueue) {
    val recent by library.recent.collectAsState(initial = emptyList())
    LibrarySongs(
        title = stringResource(Res.string.recently_played),
        emptyMessage = stringResource(Res.string.recently_played_empty),
        songs = recent,
        onPlay = queue::play,
        credit = stringResource(Res.string.shell_credit),
    )
}

@Composable
internal fun DesktopLibraryPage(library: LocalLibrary, queue: SharedQueue) {
    val saved by library.saved.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var updateFailed by remember { mutableStateOf(false) }
    LibrarySongs(
        title = stringResource(Res.string.your_library),
        emptyMessage = stringResource(Res.string.library_empty),
        songs = saved,
        onPlay = queue::play,
        onRemove = { song ->
            scope.launch {
                updateFailed = runCatching { library.setSaved(song, false) }.isFailure
            }
        },
        updateFailed = updateFailed,
    )
}

/** The whole player on one page, as a desktop window expects it: art and transport at the top, the
 *  queue under it and lyrics below, instead of the phone's bottom sheet. */
@Composable
internal fun DesktopNowPlayingPage(
    navController: NavController,
    queue: SharedQueue,
    providers: List<LyricsProvider>,
) {
    val player = LocalPlayerConnection.current ?: return
    val playback by player.state.collectAsState()
    val queued by queue.upcoming.collectAsState()
    val queueReady by queue.ready.collectAsState()
    val song = playback.song

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painter = painterResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back_button_desc),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    stringResource(Res.string.now_playing),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (song == null) {
            item {
                Text(
                    stringResource(Res.string.queue_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    song.album?.name?.let { album ->
                        Text(album, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            val duration = playback.durationMs.takeIf { it > 0 } ?: ((song.duration ?: 0) * 1000L)
            Column {
                Slider(
                    value = if (duration > 0) (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { fraction -> if (duration > 0) player.seekTo((fraction * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { player.skipToPrevious() }) { Text("⏮") }
                    TextButton(onClick = { player.togglePlayPause() }) {
                        Text(stringResource(if (playback.isPlaying) Res.string.pause else Res.string.play))
                    }
                    TextButton(onClick = { player.skipToNext() }) { Text("⏭") }
                }
            }
        }
        item {
            Text(stringResource(Res.string.lyrics), style = MaterialTheme.typography.titleLarge)
            LyricsPanel(
                song = song,
                positionMs = playback.positionMs,
                providers = providers,
                seekTo = player::seekTo,
            )
        }
        item {
            Text(stringResource(Res.string.queue), style = MaterialTheme.typography.titleLarge)
        }
        if (!queueReady) {
            item { Text(stringResource(Res.string.queue_loading), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (queued.isEmpty()) {
            item { Text(stringResource(Res.string.queue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(queued, key = { it.id }) { queuedSong ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SongRow(queuedSong, onClick = { queue.play(queuedSong) }, modifier = Modifier.weight(1f))
                    val label = stringResource(Res.string.remove_from_queue)
                    IconButton(
                        onClick = { queue.remove(queuedSong) },
                        modifier = Modifier.semantics { contentDescription = label },
                    ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
    }
}

/** A shared settings row that belongs to an Android-only page opens this instead of failing. */
@Composable
internal fun DesktopUnavailablePage(navController: NavController, title: String) {
    SubSettingsScaffold(title = title, onBack = { navController.navigateUp() }) {
        Text(stringResource(Res.string.shell_coming_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.shell_coming_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
