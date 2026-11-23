// ImporterYouTube.desktop.kt

package com.mochyy.mochymusic.importer

import com.music.innertube.YouTube
import com.music.innertube.models.AccountInfo
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.PlaylistPage
import com.music.innertube.utils.completed
import com.mochyy.mochymusic.utils.ioDispatcher
import kotlinx.coroutines.withContext

// desktop speaks to the same innertube client Android does
internal actual object ImporterYouTube {
    actual suspend fun search(query: String, filter: ImporterSearchFilter): Result<List<SongItem>> =
        YouTube.search(
            query,
            when (filter) {
                ImporterSearchFilter.SONG -> YouTube.SearchFilter.FILTER_SONG
                ImporterSearchFilter.VIDEO -> YouTube.SearchFilter.FILTER_VIDEO
            },
        ).map { result -> result.items.filterIsInstance<SongItem>() }

    actual suspend fun playlist(playlistId: String): Result<PlaylistPage> = YouTube.playlist(playlistId).completed()

    actual suspend fun album(browseId: String): Result<AlbumPage> = YouTube.album(browseId)

    actual suspend fun libraryPlaylists(): Result<List<PlaylistItem>> =
        YouTube.library("FEmusic_liked_playlists").completed().map { page -> page.items.filterIsInstance<PlaylistItem>() }

    actual suspend fun accountInfo(): Result<AccountInfo> = YouTube.accountInfo()

    // innertube answers with the http response, and a failure is what the caller watches for
    actual suspend fun likeVideo(videoId: String, like: Boolean): Result<Unit> = YouTube.likeVideo(videoId, like).map { }

    actual suspend fun createPlaylist(name: String): Result<String> =
        // innertube's own helper is a blocking call, so it runs on the io dispatcher
        withContext(ioDispatcher) { runCatching { YouTube.createPlaylist(name) } }

    actual suspend fun addVideosToPlaylist(browseId: String, videoIds: List<String>): Result<Unit> =
        YouTube.addVideosToPlaylist(browseId, videoIds)

    actual suspend fun addVideosToPlaylistWithSetVideoIds(browseId: String, videoIds: List<String>): Result<List<Pair<String, String>>> =
        YouTube.addVideosToPlaylistWithSetVideoIds(browseId, videoIds)

    actual suspend fun artistThumbnail(artistId: String): Result<String?> =
        YouTube.artist(artistId).map { page -> page.artist.thumbnail }
}
