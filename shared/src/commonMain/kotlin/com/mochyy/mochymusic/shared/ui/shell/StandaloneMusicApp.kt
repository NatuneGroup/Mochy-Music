package com.mochyy.mochymusic.shared.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.mochyy.mochymusic.shared.library.LocalLibrary
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider
import com.mochyy.mochymusic.shared.lyrics.PlayerLyrics
import com.mochyy.mochymusic.shared.lyrics.findPlayerLyrics
import com.mochyy.mochymusic.shared.lyrics.suspendRunCatching
import com.mochyy.mochymusic.shared.media.SearchSource
import com.mochyy.mochymusic.shared.playback.PlayerController
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.library_empty
import com.mochyy.mochymusic.shared.resources.library_remove
import com.mochyy.mochymusic.shared.resources.library_save
import com.mochyy.mochymusic.shared.resources.library_update_failed
import com.mochyy.mochymusic.shared.resources.lyrics
import com.mochyy.mochymusic.shared.resources.lyrics_missing
import com.mochyy.mochymusic.shared.resources.lyrics_refresh
import com.mochyy.mochymusic.shared.resources.lyrics_refresh_failed
import com.mochyy.mochymusic.shared.resources.lyrics_source
import com.mochyy.mochymusic.shared.resources.lyrics_source_auto
import com.mochyy.mochymusic.shared.resources.now_playing
import com.mochyy.mochymusic.shared.resources.now_playing_settings
import com.mochyy.mochymusic.shared.resources.settings_write_failed
import com.mochyy.mochymusic.shared.resources.show_format_badge
import com.mochyy.mochymusic.shared.resources.show_format_sample_rate
import com.mochyy.mochymusic.shared.resources.queue
import com.mochyy.mochymusic.shared.resources.queue_empty
import com.mochyy.mochymusic.shared.resources.recently_played
import com.mochyy.mochymusic.shared.resources.recently_played_empty
import com.mochyy.mochymusic.shared.resources.shell_credit
import com.mochyy.mochymusic.shared.resources.your_library
import com.mochyy.mochymusic.shared.resources.remove_from_queue
import com.mochyy.mochymusic.shared.resources.skip_next
import com.mochyy.mochymusic.shared.settings.PlaybackPreferenceKeys
import com.mochyy.mochymusic.shared.settings.PlaybackPreferences
import com.mochyy.mochymusic.shared.ui.player.MiniPlayerBar
import com.mochyy.mochymusic.shared.ui.search.SearchScreen
import com.mochyy.mochymusic.shared.ui.search.SongRow
import com.music.innertube.models.SongItem
import kotlinx.coroutines.launch
import okio.IOException
import org.jetbrains.compose.resources.stringResource

private enum class PlayerPanel { Queue, Lyrics, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StandaloneMusicApp(
    source: SearchSource,
    player: PlayerController,
    lyricsProviders: List<LyricsProvider>,
    settings: PlaybackPreferences,
    library: LocalLibrary,
    modifier: Modifier = Modifier,
) {
    val playback by player.state.collectAsState()
    var queued by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var showPlayer by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(PlayerPanel.Queue) }
    val preferences by settings.values.collectAsState(initial = emptyPreferences())
    val showBadge = preferences[PlaybackPreferenceKeys.showFormatBadge] ?: true
    val showSampleRate = preferences[PlaybackPreferenceKeys.showSampleRate] ?: false
    var settingsFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saved by library.saved.collectAsState(initial = emptyList())
    val recent by library.recent.collectAsState(initial = emptyList())
    var libraryUpdateFailed by remember { mutableStateOf(false) }
    var lastRecorded by remember { mutableStateOf<String?>(null) }

    fun setSaved(song: SongItem, save: Boolean) {
        scope.launch {
            libraryUpdateFailed = suspendRunCatching { library.setSaved(song, save) }.isFailure
        }
    }

    val playingSong = playback.song
    LaunchedEffect(playingSong?.id, playback.isPlaying) {
        if (playingSong != null && playback.isPlaying && playingSong.id != lastRecorded) {
            suspendRunCatching { library.recordPlay(playingSong) }
                .onSuccess { lastRecorded = playingSong.id }
        }
    }

    fun setPreference(key: Preferences.Key<Boolean>, value: Boolean) {
        scope.launch {
            settingsFailed = try {
                settings.set(key, value)
                false
            } catch (_: IOException) {
                true
            }
        }
    }

    fun play(song: SongItem) {
        queued = queued.filterNot { it.id == song.id }
        player.play(song)
    }

    fun playNext() {
        queued.firstOrNull()?.let(::play)
    }

    LaunchedEffect(playback.hasEnded) {
        if (playback.hasEnded) playNext()
    }

    MochyShell(
        modifier = modifier,
        initialTab = ShellTab.Search,
        bottomBar = {
            MiniPlayerBar(
                playback, player::togglePlayPause, player::seekTo, onOpen = { showPlayer = true },
                showFormatBadge = showBadge, showSampleRate = showSampleRate,
            )
        },
    ) { tab ->
        when (tab) {
            ShellTab.Search -> SearchScreen(
                source = source,
                onPlay = ::play,
                onQueue = { song ->
                    if (queued.none { it.id == song.id }) queued = queued + song
                    panel = PlayerPanel.Queue
                    showPlayer = true
                },
            )
            ShellTab.Home -> LibrarySongs(
                title = stringResource(Res.string.recently_played),
                emptyMessage = stringResource(Res.string.recently_played_empty),
                songs = recent,
                onPlay = ::play,
                credit = stringResource(Res.string.shell_credit),
            )
            ShellTab.Library -> LibrarySongs(
                title = stringResource(Res.string.your_library),
                emptyMessage = stringResource(Res.string.library_empty),
                songs = saved,
                onPlay = ::play,
                onRemove = { setSaved(it, false) },
                updateFailed = libraryUpdateFailed,
            )
        }
        true
    }

    if (showPlayer) {
        ModalBottomSheet(onDismissRequest = { showPlayer = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                playback.song?.let { song ->
                    Text(stringResource(Res.string.now_playing), style = MaterialTheme.typography.labelMedium)
                    Text(song.title, style = MaterialTheme.typography.headlineSmall)
                    val isSaved = saved.any { it.id == song.id }
                    TextButton(onClick = { setSaved(song, !isSaved) }) {
                        Text(stringResource(if (isSaved) Res.string.library_remove else Res.string.library_save))
                    }
                    if (libraryUpdateFailed) {
                        Text(stringResource(Res.string.library_update_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { panel = PlayerPanel.Queue }, enabled = panel != PlayerPanel.Queue) {
                        Text(stringResource(Res.string.queue))
                    }
                    TextButton(onClick = { panel = PlayerPanel.Lyrics }, enabled = panel != PlayerPanel.Lyrics && playback.song != null) {
                        Text(stringResource(Res.string.lyrics))
                    }
                    TextButton(onClick = { panel = PlayerPanel.Settings }, enabled = panel != PlayerPanel.Settings) {
                        Text(stringResource(Res.string.now_playing_settings))
                    }
                }
                val song = playback.song
                if (panel == PlayerPanel.Settings) {
                    PlaybackSettingRow(stringResource(Res.string.show_format_badge), showBadge) {
                        setPreference(PlaybackPreferenceKeys.showFormatBadge, it)
                    }
                    PlaybackSettingRow(stringResource(Res.string.show_format_sample_rate), showSampleRate, enabled = showBadge) {
                        setPreference(PlaybackPreferenceKeys.showSampleRate, it)
                    }
                    if (settingsFailed) {
                        Text(stringResource(Res.string.settings_write_failed), color = MaterialTheme.colorScheme.error)
                    }
                } else if (panel == PlayerPanel.Lyrics && song != null) {
                    LyricsPanel(song, playback.positionMs, lyricsProviders, player::seekTo)
                } else if (queued.isEmpty()) {
                    Text(stringResource(Res.string.queue_empty))
                } else {
                    TextButton(onClick = ::playNext) { Text(stringResource(Res.string.skip_next)) }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(queued, key = { it.id }) { song ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SongRow(song, onClick = { play(song) }, modifier = Modifier.weight(1f))
                                val label = stringResource(Res.string.remove_from_queue)
                                IconButton(
                                    onClick = { queued = queued.filterNot { it.id == song.id } },
                                    modifier = Modifier.semantics { contentDescription = label },
                                ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySongs(
    title: String,
    emptyMessage: String,
    songs: List<SongItem>,
    onPlay: (SongItem) -> Unit,
    onRemove: ((SongItem) -> Unit)? = null,
    credit: String? = null,
    updateFailed: Boolean = false,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(vertical = 24.dp))
        }
        if (updateFailed) item {
            Text(stringResource(Res.string.library_update_failed), color = MaterialTheme.colorScheme.error)
        }
        if (songs.isEmpty()) item {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(songs, key = { it.id }) { song ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SongRow(song, onClick = { onPlay(song) }, modifier = Modifier.weight(1f))
                if (onRemove != null) {
                    val label = stringResource(Res.string.library_remove)
                    IconButton(
                        onClick = { onRemove(song) },
                        modifier = Modifier.semantics { contentDescription = label },
                    ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
        if (credit != null) item {
            Text(
                credit, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun PlaybackSettingRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun LyricsPanel(
    song: SongItem,
    positionMs: Long,
    providers: List<LyricsProvider>,
    seekTo: (Long) -> Unit,
) {
    var lyrics by remember(song.id) { mutableStateOf<PlayerLyrics?>(null) }
    var loading by remember(song.id) { mutableStateOf(true) }
    var failed by remember(song.id) { mutableStateOf(false) }
    var selectedProvider by remember(song.id) { mutableStateOf<LyricsProvider?>(null) }
    var refresh by remember(song.id) { mutableIntStateOf(0) }
    var menuOpen by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id, providers, selectedProvider, refresh) {
        loading = true
        failed = false
        val result = findPlayerLyrics(song, selectedProvider?.let { listOf(it) } ?: providers)
        if (result != null) lyrics = result
        failed = result == null
        loading = false
    }
    val document = lyrics
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text(stringResource(Res.string.lyrics_source))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.lyrics_source_auto)) },
                    onClick = {
                        selectedProvider = null
                        menuOpen = false
                    },
                )
                providers.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.name) },
                        onClick = {
                            selectedProvider = provider
                            menuOpen = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.lyrics_refresh)) },
                    onClick = {
                        refresh++
                        menuOpen = false
                    },
                )
            }
        }
        if (document != null) Text(document.provider, style = MaterialTheme.typography.labelMedium)
    }
    if (document == null) {
        if (loading) CircularProgressIndicator(Modifier.padding(16.dp))
        else Text(stringResource(Res.string.lyrics_missing))
    } else {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(Res.string.lyrics_refresh_failed), color = MaterialTheme.colorScheme.error)
        val list = rememberLazyListState()
        val active = if (document.synced) document.lines.indexOfLast { it.time <= positionMs } else -1
        LaunchedEffect(active, document) {
            if (active >= 0) list.animateScrollToItem((active - 1).coerceAtLeast(0))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), state = list) {
            itemsIndexed(document.lines) { index, line ->
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (!document.synced || index == active) 1f else 0.55f),
                    modifier = Modifier.fillMaxWidth()
                        .then(if (document.synced) Modifier.clickable { seekTo(line.time) } else Modifier)
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}
