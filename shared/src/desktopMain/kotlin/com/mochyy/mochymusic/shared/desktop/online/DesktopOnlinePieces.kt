// DesktopOnlinePieces.kt
// The small pieces the three online pages share: a message for "loading" and "couldn't reach it", an
// album card, an artist row and the one place that decides what a tap on a YouTube item does. Keeping
// the click rule here means the explore page and a browse section open the same pages the same way.

package com.mochyy.mochymusic.shared.desktop.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.shared.desktop.navigation.DesktopRoutes
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.add_to_queue
import com.mochyy.mochymusic.shared.resources.desktop_online_empty
import com.mochyy.mochymusic.shared.resources.desktop_online_failed
import com.mochyy.mochymusic.shared.resources.playlist_add
import com.mochyy.mochymusic.shared.resources.retry
import com.mochyy.mochymusic.shared.ui.search.SongRow
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A title over a horizontal strip of cards: the shape every section of the online pages uses. */
@Composable
internal fun DesktopCardStrip(
    title: String,
    items: List<YTItem>,
    navController: NavHostController,
    queue: SharedQueue,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id + it.title }) { item ->
                DesktopOnlineCard(item = item, navController = navController, queue = queue)
            }
        }
    }
}

/** One card: an album or playlist cover, or an artist's picture; songs are drawn as rows instead. */
@Composable
internal fun DesktopOnlineCard(
    item: YTItem,
    navController: NavHostController,
    queue: SharedQueue,
) {
    val subtitle = when (item) {
        is AlbumItem -> item.artists?.joinToString(", ") { it.name }
        is ArtistItem -> null
        is PlaylistItem -> item.author?.name
        else -> null
    }
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable { openItem(item, navController, queue) },
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A song with its queue button, the row the browsing pages and the collection page both use. */
@Composable
internal fun DesktopOnlineSongRow(song: SongItem, queue: SharedQueue) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SongRow(song, onClick = { queue.play(song) }, modifier = Modifier.weight(1f))
        val label = stringResource(Res.string.add_to_queue)
        IconButton(onClick = { queue.add(song) }) {
            Icon(
                painter = painterResource(Res.drawable.playlist_add),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A songs-only list, for a section whose items are all playable. */
@Composable
internal fun DesktopSongStrip(
    title: String?,
    songs: List<SongItem>,
    queue: SharedQueue,
) {
    if (songs.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        title?.let {
            Text(it, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
        }
        songs.forEach { song -> DesktopOnlineSongRow(song, queue) }
    }
}

/** The one line a page shows while it has nothing to draw: loading, or the error with a retry. */
@Composable
internal fun DesktopOnlineMessage(text: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}

@Composable
internal fun DesktopOnlineFailed(onRetry: () -> Unit) {
    DesktopOnlineMessage(stringResource(Res.string.desktop_online_failed), onRetry = onRetry)
}

@Composable
internal fun DesktopOnlineEmpty() {
    DesktopOnlineMessage(stringResource(Res.string.desktop_online_empty))
}

/**
 * What a tap on an item opens. Songs play and join the queue; albums and playlists open their own
 * page; an artist has no page of its own here yet, so it stays where it is instead of failing.
 */
internal fun openItem(item: YTItem, navController: NavHostController, queue: SharedQueue) {
    when (item) {
        is SongItem -> queue.play(item)
        is AlbumItem -> navController.navigate(DesktopRoutes.album(item.browseId)) { launchSingleTop = true }
        is PlaylistItem -> navController.navigate(DesktopRoutes.playlist(item.id)) { launchSingleTop = true }
        // an artist channel needs the artist page's own parser; the artist cards are not opened here
        is ArtistItem -> Unit
        else -> Unit
    }
}

/** A picture with a fallback colour, used by the collection headers. */
@Composable
internal fun DesktopOnlineArt(url: String?, size: Int, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
