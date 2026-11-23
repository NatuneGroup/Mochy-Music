// ImporterYouTube.kt

package com.mochyy.mochymusic.importer

import com.music.innertube.models.AccountInfo
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.PlaylistPage

/** the search the matcher ranks, the same two youtube filters the app always asked for */
internal enum class ImporterSearchFilter { SONG, VIDEO }

/**
 * The YouTube Music requests an import makes. The innertube client only exists on Android and
 * desktop, so common code asks this seam and each platform answers through it: both JVM platforms
 * delegate to the same YouTube object with the same requests and pages, and iOS, which has no
 * client, fails every call cleanly (an import there reads links and files, not YouTube).
 */
internal expect object ImporterYouTube {
    /** the top results for one of the two filters, songs and videos alike */
    suspend fun search(query: String, filter: ImporterSearchFilter): Result<List<SongItem>>

    /** a playlist with every page of its songs read, the way imports and re-syncs list it */
    suspend fun playlist(playlistId: String): Result<PlaylistPage>

    suspend fun album(browseId: String): Result<AlbumPage>

    /** the signed-in account's playlists (liked music included), every page read */
    suspend fun libraryPlaylists(): Result<List<PlaylistItem>>

    suspend fun accountInfo(): Result<AccountInfo>

    suspend fun likeVideo(videoId: String, like: Boolean): Result<Unit>

    /** creates an empty playlist and returns its new id */
    suspend fun createPlaylist(name: String): Result<String>

    suspend fun addVideosToPlaylist(browseId: String, videoIds: List<String>): Result<Unit>

    /** each (videoId, setVideoId) youtube answered with, so later removals can name the entry */
    suspend fun addVideosToPlaylistWithSetVideoIds(browseId: String, videoIds: List<String>): Result<List<Pair<String, String>>>

    /** the artist channel's picture, for the author line of an imported YouTube album or playlist */
    suspend fun artistThumbnail(artistId: String): Result<String?>
}

/** thrown on a platform with no YouTube client, where imports of links and files still work */
internal class ImportYouTubeUnavailableException : Exception("YouTube Music isn't available on this platform yet")
