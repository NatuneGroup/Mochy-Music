// DesktopExplorePage.kt
// YouTube Music's explore page on the desktop: the moods and genres strip, and the new release
// albums, both straight from the shared YouTubeRepository. Tapping a mood opens a browse page; an
// album opens its own page. Nothing is cached between visits: the page asks again each time it opens,
// the way the phone's Explore tab does.

package com.mochyy.mochymusic.shared.desktop.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
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
import com.mochyy.mochymusic.shared.desktop.navigation.DesktopRoutes
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.mochyy.mochymusic.shared.resources.Res
import com.mochyy.mochymusic.shared.resources.charts
import com.mochyy.mochymusic.shared.resources.desktop_online_loading
import com.mochyy.mochymusic.shared.resources.explore
import com.mochyy.mochymusic.shared.resources.mood_and_genres
import com.mochyy.mochymusic.shared.resources.new_release_albums
import com.music.innertube.pages.ExplorePage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun DesktopExplorePage(navController: NavHostController, queue: SharedQueue) {
    val youTube = koinInject<YouTubeRepository>()
    var page by remember { mutableStateOf<ExplorePage?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        failed = false
        val result = youTube.explore()
        page = result.getOrNull()
        failed = result.isFailure
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(Res.string.explore),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val loaded = page
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
        if (loaded.moodAndGenres.isNotEmpty()) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.mood_and_genres),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        // the full charts page, one tap from the chips
                        AssistChip(
                            onClick = { navController.navigate(DesktopRoutes.Charts) { launchSingleTop = true } },
                            label = { Text(stringResource(Res.string.charts)) },
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(loaded.moodAndGenres, key = { it.title }) { mood ->
                            AssistChip(
                                onClick = {
                                    navController.navigate(DesktopRoutes.browse(mood.endpoint.browseId, mood.endpoint.params)) {
                                        launchSingleTop = true
                                    }
                                },
                                label = { Text(mood.title) },
                            )
                        }
                    }
                }
            }
        }
        item {
            DesktopCardStrip(
                title = stringResource(Res.string.new_release_albums),
                items = loaded.newReleaseAlbums,
                navController = navController,
                queue = queue,
            )
        }
        if (loaded.moodAndGenres.isEmpty() && loaded.newReleaseAlbums.isEmpty()) {
            item { DesktopOnlineEmpty() }
        }
    }
}
