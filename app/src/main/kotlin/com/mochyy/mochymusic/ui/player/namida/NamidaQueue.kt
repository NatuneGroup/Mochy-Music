// namida's queue: a page that slides over the player, the songs as rounded tiles with the
// current one carried on a gradient of the song's colour, a header that counts the queue and the
// time left in it, and a bottom row of round buttons — jump to what's playing, shuffle, and the
// remove menu (duplicates, previous, next, all).

package com.mochyy.mochymusic.ui.player.namida

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.player.models.QueueItemData
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.launch

/** the queue's tile: how big the artwork is, the tile's corner and the bottom row's height */
private val NamidaQueueArtwork = 54.dp
private val NamidaQueueCorner = 12.dp
private val NamidaQueueBottomRow = 48.dp

/** how far down the queue's list the playing song is parked, as a share of the pane */
private const val NamidaQueuePark = 0.20f

/**
 * The queue over the player: [onClose] folds it back down. The current song is highlighted on a
 * gradient of the song's colour and the list opens parked on it, namida's way.
 */
@Composable
internal fun NamidaQueue(
    palette: NamidaPalette,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    val queue by connection.queueItems.collectAsState()
    val currentIndex by connection.currentWindowIndex.collectAsState()
    val playing by connection.uiState.trackInfo.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // open parked on the playing song, a fifth of the way down the list, namida's follower spot
    LaunchedEffect(Unit) {
        if (currentIndex in queue.indices) {
            val viewport = 720
            runCatching { listState.scrollToItem(currentIndex, -(viewport * NamidaQueuePark).toInt()) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(palette.surface),
    ) {
        // the header: the count, the time left, and the close chevron
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.queue),
                    color = palette.onSurface,
                    fontSize = 15.sp,
                    fontFamily = NamidaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                )
                val remaining = queue.drop(maxOf(currentIndex, 0)).sumOf { it.duration }.coerceAtLeast(0L)
                if (queue.isNotEmpty() && remaining > 0L) {
                    Text(
                        text = "${(currentIndex + 1).coerceIn(1, queue.size)}/${queue.size} • ${makeTimeString(remaining)}",
                        color = palette.onSurfaceSoft,
                        fontSize = 13.sp,
                        fontFamily = NamidaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    tint = palette.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(queue, key = { it.uid }) { item ->
                val isCurrent = item.mediaId == playing.mediaId
                NamidaQueueTile(palette, item, isCurrent) {
                    connection.playQueueItem(item.uid)
                }
            }
        }

        // the bottom utils row: jump to the playing song, shuffle, each a gradient circle
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(NamidaQueueBottomRow)
                .padding(horizontal = 14.dp),
        ) {
            val transport by connection.uiState.transportState.collectAsState()
            NamidaQueueButton(palette, R.drawable.music_history, stringResource(R.string.namida_jump_current)) {
                if (currentIndex in queue.indices) {
                    runCatching {
                        scope.launch { listState.animateScrollToItem(currentIndex, -(720 * NamidaQueuePark).toInt()) }
                    }
                }
            }
            NamidaQueueButton(palette, R.drawable.shuffle, stringResource(R.string.shuffle), transport.shuffleModeEnabled) {
                connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled
            }
            NamidaQueueButton(palette, R.drawable.playlist_add, stringResource(R.string.ps_action_add_to_playlist)) {
                connection.playQueueItemNext(queue.getOrNull(currentIndex)?.uid ?: return@NamidaQueueButton)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** one song of the queue: artwork, two lines, the length; the playing one on the colour gradient. */
@Composable
private fun NamidaQueueTile(
    palette: NamidaPalette,
    item: QueueItemData,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(NamidaQueueCorner))
            .background(
                when {
                    isCurrent -> Brush.linearGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.55f).compositeOver(palette.surface),
                            palette.accent.copy(alpha = 0.22f).compositeOver(palette.surface),
                        ),
                    )
                    else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        AsyncImage(
            model = item.artworkUri,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(NamidaQueueArtwork)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = if (isCurrent) Color.White.copy(alpha = 0.67f) else palette.onSurface,
                fontSize = 15.sp,
                fontFamily = NamidaFontFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                color = if (isCurrent) Color.White.copy(alpha = 0.55f) else palette.onSurfaceSoft,
                fontSize = 13.sp,
                fontFamily = NamidaFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.duration > 0L) {
            Text(
                text = makeTimeString(item.duration),
                color = if (isCurrent) Color.White.copy(alpha = 0.67f) else palette.onSurfaceSoft,
                fontSize = 13.sp,
                fontFamily = NamidaFontFamily,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** one of the bottom row's gradient circles, namida's round util buttons. */
@Composable
private fun NamidaQueueButton(
    palette: NamidaPalette,
    icon: Int,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (active) palette.accent.copy(alpha = 0.6f).compositeOver(palette.surface)
                else palette.accent.copy(alpha = 0.18f).compositeOver(palette.surface),
            )
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (active) Color.White else palette.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}
