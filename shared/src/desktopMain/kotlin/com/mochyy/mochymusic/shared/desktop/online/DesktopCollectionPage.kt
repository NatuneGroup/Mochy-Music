// DesktopCollectionPage.kt
// One album or one playlist, opened from a card anywhere on the online pages: cover, title, the songs
// with a play button that queues the whole record, and whatever the page suggests underneath. The two
// kinds share this page because they differ only in which request fills it and which field names the
// cover.

package com.mochyy.mochymusic.shared.desktop.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mochyy.mochymusic.api.YouTubeRepository
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.arrow_back
import com.mochyy.mochymusic.shared.resources.back_button_desc
import com.mochyy.mochymusic.shared.resources.desktop_online_loading
import com.mochyy.mochymusic.shared.resources.play_all
import com.mochyy.mochymusic.shared.resources.releases_for_you
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Which request fills the collection page. */
internal enum class CollectionKind(val routeValue: String) {
    ALBUM("album"),
    PLAYLIST("playlist"),
    ;

    companion object {
        fun fromRoute(value: String): CollectionKind = entries.firstOrNull { it.routeValue == value } ?: ALBUM
    }
}

/** what both pages answer with, flattened to the parts the page draws */
private class CollectionView(
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val songs: List<SongItem>,
    val more: List<YTItem> = emptyList(),
)

@Composable
internal fun DesktopCollectionPage(
    navController: NavHostController,
    kind: CollectionKind,
    id: String,
    queue: SharedQueue,
) {
    val youTube = koinInject<YouTubeRepository>()
    var view by remember(kind, id) { mutableStateOf<CollectionView?>(null) }
    var failed by remember(kind, id) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(kind, id, reload) {
        failed = false
        val loaded = when (kind) {
            CollectionKind.ALBUM -> youTube.album(id).map { page ->
                CollectionView(
                    title = page.album.title,
                    subtitle = page.album.artists?.joinToString(", ") { it.name },
                    thumbnail = page.album.thumbnail,
                    songs = page.songs,
                    more = page.releasesForYou.takeIf { it.isNotEmpty() } ?: page.otherVersions,
                )
            }

            CollectionKind.PLAYLIST -> youTube.playlist(id).map { page ->
                CollectionView(
                    title = page.playlist.title,
                    subtitle = page.playlist.author?.name,
                    thumbnail = page.playlist.thumbnail,
                    songs = page.songs,
                    more = page.related.orEmpty(),
                )
            }
        }
        view = loaded.getOrNull()
        failed = loaded.isFailure
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            }
        }
        val loaded = view
        if (loaded == null) {
            item {
                if (failed) {
                    DesktopOnlineFailed(onRetry = { reload++ })
                } else {
                    DesktopOnlineMessage(stringResource(Res.string.desktop_online_loading))
                }
            }
            return@LazyColumn
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopOnlineArt(url = loaded.thumbnail, size = 160)
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        loaded.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    loaded.subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            // the record replaces Up next, then plays from its first song: the same
                            // thing a phone does when a whole album is played
                            queue.upcoming.value.forEach { queue.remove(it) }
                            loaded.songs.firstOrNull()?.let { first ->
                                queue.play(first)
                                loaded.songs.drop(1).forEach { queue.add(it) }
                            }
                        },
                        enabled = loaded.songs.isNotEmpty(),
                    ) { Text(stringResource(Res.string.play_all)) }
                }
            }
        }
        item {
            DesktopSongStrip(title = null, songs = loaded.songs, queue = queue)
        }
        if (loaded.songs.isEmpty()) {
            item { DesktopOnlineEmpty() }
        }
        if (loaded.more.isNotEmpty()) {
            item {
                DesktopCardStrip(
                    title = if (kind == CollectionKind.ALBUM) stringResource(Res.string.releases_for_you) else "",
                    items = loaded.more,
                    navController = navController,
                    queue = queue,
                )
            }
        }
    }
}
