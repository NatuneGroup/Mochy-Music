// ImporterYouTube.ios.kt

package com.mochyy.mochymusic.importer

import com.music.innertube.models.AccountInfo
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.PlaylistPage

// there is no YouTube Music client on iOS yet; every call fails cleanly and imports read links and files only
internal actual object ImporterYouTube {
    actual suspend fun search(query: String, filter: ImporterSearchFilter): Result<List<SongItem>> = unavailable()

    actual suspend fun playlist(playlistId: String): Result<PlaylistPage> = unavailable()

    actual suspend fun album(browseId: String): Result<AlbumPage> = unavailable()

    actual suspend fun libraryPlaylists(): Result<List<PlaylistItem>> = unavailable()

    actual suspend fun accountInfo(): Result<AccountInfo> = unavailable()

    actual suspend fun likeVideo(videoId: String, like: Boolean): Result<Unit> = unavailable()

    actual suspend fun createPlaylist(name: String): Result<String> = unavailable()

    actual suspend fun addVideosToPlaylist(browseId: String, videoIds: List<String>): Result<Unit> = unavailable()

    actual suspend fun addVideosToPlaylistWithSetVideoIds(browseId: String, videoIds: List<String>): Result<List<Pair<String, String>>> = unavailable()

    actual suspend fun artistThumbnail(artistId: String): Result<String?> = unavailable()

    private fun <T> unavailable(): Result<T> = Result.failure(ImportYouTubeUnavailableException())
}
