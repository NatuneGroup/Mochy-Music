// IosPlayerSheet.kt
// What the mini player opens on iOS: the song, its stream badge, the seek bar and transport, the
// lyrics of the song that is playing, and Up next. Every piece is a shared one (SongRow,
// StreamFormatBadge, findPlayerLyrics, formatDuration); this file only lays them out and keeps the
// small state a sheet owns (the drag in progress, the chosen lyrics source).

package com.mochyy.mochymusic.shared.ios.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.shared.lyrics.LyricsProvider
import com.mochyy.mochymusic.shared.lyrics.PlayerLyrics
import com.mochyy.mochymusic.shared.lyrics.findPlayerLyrics
import com.mochyy.mochymusic.shared.playback.PlayerState
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.library_remove
import com.mochyy.mochymusic.shared.resources.library_save
import com.mochyy.mochymusic.shared.resources.lyrics
import com.mochyy.mochymusic.shared.resources.lyrics_missing
import com.mochyy.mochymusic.shared.resources.lyrics_refresh
import com.mochyy.mochymusic.shared.resources.lyrics_refresh_failed
import com.mochyy.mochymusic.shared.resources.lyrics_source
import com.mochyy.mochymusic.shared.resources.lyrics_source_auto
import com.mochyy.mochymusic.shared.resources.now_playing
import com.mochyy.mochymusic.shared.resources.pause
import com.mochyy.mochymusic.shared.resources.play
import com.mochyy.mochymusic.shared.resources.player_pause
import com.mochyy.mochymusic.shared.resources.player_play
import com.mochyy.mochymusic.shared.resources.queue
import com.mochyy.mochymusic.shared.resources.queue_empty
import com.mochyy.mochymusic.shared.resources.queue_load_failed
import com.mochyy.mochymusic.shared.resources.queue_loading
import com.mochyy.mochymusic.shared.resources.queue_write_failed
import com.mochyy.mochymusic.shared.resources.remove_from_queue
import com.mochyy.mochymusic.shared.resources.skip_next
import com.mochyy.mochymusic.shared.resources.previous
import com.mochyy.mochymusic.shared.ui.player.StreamFormatBadge
import com.mochyy.mochymusic.shared.ui.player.streamBadgeText
import com.mochyy.mochymusic.shared.ui.search.SongRow
import com.mochyy.mochymusic.shared.ui.search.formatDuration
import com.music.innertube.models.SongItem
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun IosPlayerSheet(
    state: PlayerState,
    upcoming: List<SongItem>,
    queueReady: Boolean,
    queueStorageUnavailable: Boolean,
    queueWriteFailed: Boolean,
    providers: List<LyricsProvider>,
    showFormatBadge: Boolean,
    showSampleRate: Boolean,
    isSaved: Boolean,
    onSave: (SongItem, Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayUpcoming: (SongItem) -> Unit,
    onRemoveUpcoming: (SongItem) -> Unit,
) {
    val song = state.song
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (song != null) {
            item {
                Text(stringResource(Res.string.now_playing), style = MaterialTheme.typography.labelMedium)
            }
            item {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            state.error ?: song.artists.joinToString { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.error != null) Color(0xFFFF8A80) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showFormatBadge) {
                        streamBadgeText(state.format, showSampleRate)?.let { StreamFormatBadge(it, height = 18.dp) }
                    }
                }
            }
            item {
                TextButton(onClick = { onSave(song, !isSaved) }) {
                    Text(stringResource(if (isSaved) Res.string.library_remove else Res.string.library_save))
                }
            }
            item { SeekRow(state, onSeek) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransportButton(Res.drawable.skip_previous, stringResource(Res.string.previous), onPrevious)
                    PlayButton(state.isLoading, state.isPlaying, onTogglePlay)
                    TransportButton(Res.drawable.skip_next, stringResource(Res.string.skip_next), onNext)
                }
            }
            item { LyricsSection(song, state.positionMs, providers, onSeek) }
        }

        item {
            Text(stringResource(Res.string.queue), style = MaterialTheme.typography.titleMedium)
        }
        if (!queueReady) {
            item { QueueNote(text = stringResource(Res.string.queue_loading), error = false) }
        } else {
            if (queueStorageUnavailable) {
                item { QueueNote(text = stringResource(Res.string.queue_load_failed), error = true) }
            }
            if (queueWriteFailed) {
                item { QueueNote(text = stringResource(Res.string.queue_write_failed), error = true) }
            }
            if (upcoming.isEmpty()) {
                item { QueueNote(text = stringResource(Res.string.queue_empty), error = false) }
            }
            itemsIndexed(upcoming, key = { _, item -> item.id }) { _, queued ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SongRow(
                        song = queued,
                        onClick = { onPlayUpcoming(queued) },
                        modifier = Modifier.weight(1f),
                    )
                    val removeLabel = stringResource(Res.string.remove_from_queue)
                    IconButton(
                        onClick = { onRemoveUpcoming(queued) },
                        modifier = Modifier.semantics { contentDescription = removeLabel },
                    ) {
                        Text("−", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SeekRow(state: PlayerState, onSeek: (Long) -> Unit) {
    // while a finger is on the slider the label follows it, not the player's 250 ms poll
    var dragMs by remember(state.song?.id) { mutableStateOf<Long?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val position = (dragMs ?: state.positionMs).coerceIn(0L, duration)
    Column {
        Slider(
            value = position.toFloat() / duration.toFloat(),
            onValueChange = { fraction -> dragMs = (fraction * duration).toLong() },
            onValueChangeFinished = {
                dragMs?.let(onSeek)
                dragMs = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration(position),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatDuration(state.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportButton(icon: DrawableResource, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun PlayButton(loading: Boolean, playing: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
            )
        } else {
            IconButton(onClick = onClick, modifier = Modifier.size(72.dp)) {
                Image(
                    painter = painterResource(if (playing) Res.drawable.player_pause else Res.drawable.player_play),
                    contentDescription = stringResource(if (playing) Res.string.pause else Res.string.play),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsSection(
    song: SongItem,
    positionMs: Long,
    providers: List<LyricsProvider>,
    onSeek: (Long) -> Unit,
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
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.lyrics), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
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
        }
        if (document == null) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text(stringResource(Res.string.lyrics_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) {
                Text(stringResource(Res.string.lyrics_refresh_failed), color = MaterialTheme.colorScheme.error)
            }
            Text(document.provider, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val state = rememberLazyListState()
            val active = if (document.synced) document.lines.indexOfLast { it.time <= positionMs } else -1
            LaunchedEffect(active, document) {
                if (active >= 0) state.animateScrollToItem((active - 1).coerceAtLeast(0))
            }
            Column(Modifier.fillMaxWidth().height(240.dp).padding(top = 6.dp)) {
                LazyColumn(state = state) {
                    itemsIndexed(document.lines) { index, line ->
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (!document.synced || index == active) 1f else 0.55f,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (document.synced) Modifier.clickable { onSeek(line.time) } else Modifier)
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueNote(text: String, error: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
