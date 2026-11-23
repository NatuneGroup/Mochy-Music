// DesktopBrowsePage.kt
// Any YouTube Music browse page as the desktop draws it: the moods and genres chips on Explore land
// here, and so does anything else that only knows a browse id. Sections of songs become playable rows,
// sections of albums and playlists become cards that open their own page.

package com.mochyy.mochymusic.shared.desktop.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mochyy.mochymusic.api.YouTubeRepository
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.arrow_back
import com.mochyy.mochymusic.shared.resources.back_button_desc
import com.mochyy.mochymusic.shared.resources.desktop_online_loading
import com.music.innertube.models.SongItem
import com.music.innertube.pages.BrowseResult
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun DesktopBrowsePage(
    navController: NavHostController,
    browseId: String,
    params: String?,
    queue: SharedQueue,
) {
    val youTube = koinInject<YouTubeRepository>()
    var result by remember(browseId, params) { mutableStateOf<BrowseResult?>(null) }
    var failed by remember(browseId, params) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(browseId, params, reload) {
        failed = false
        val loaded = youTube.browse(browseId, params)
        result = loaded.getOrNull()
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
                Text(
                    result?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val loaded = result
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
        if (loaded.items.isEmpty()) {
            item { DesktopOnlineEmpty() }
        }
        for (section in loaded.items) {
            // a section can mix songs and cards; each kind keeps its own shape
            val songs = section.items.filterIsInstance<SongItem>()
            val cards = section.items.filterNot { it is SongItem }
            if (songs.isNotEmpty()) {
                item(key = "songs-${section.title}") {
                    DesktopSongStrip(title = section.title, songs = songs, queue = queue)
                }
            }
            if (cards.isNotEmpty()) {
                item(key = "cards-${section.title}") {
                    DesktopCardStrip(
                        title = section.title ?: "",
                        items = cards,
                        navController = navController,
                        queue = queue,
                    )
                }
            }
        }
    }
}
