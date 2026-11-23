// HomeFeedLogic.kt
//
// Home's feed shelves: what YouTube's home page is folded into, the speed dial behind the hero, and
// the models those rows carry. All of it is model work over the shared entities and the innertube
// page models, so it reads the same on every platform. HomeViewModel (still in :app while its
// database, Hilt and cache side move) does the fetching and calls in here.

package com.mochyy.mochymusic.viewmodels

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.HomePage
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.LocalItem
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.utils.ArtistVideos
import com.mochyy.mochymusic.utils.FeedVideoKind
import com.mochyy.mochymusic.utils.HomeVideo
import kotlinx.serialization.Serializable

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

@Serializable
data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

/** a song by one of the artists picked in onboarding, put forward on a new install's hero */
data class StarterSong(
    val song: SongItem,
    val artistName: String,
    val artistThumbnail: String?,
)

object HomeFeedLogic {

    /** YouTube's own all-video shelves in the feed, by section index */
    fun feedVideoKinds(page: HomePage?): Map<Int, FeedVideoKind> =
        page?.sections.orEmpty()
            .mapIndexedNotNull { index, section ->
                ArtistVideos.feedShelfKind(section.title, section.items)?.let { index to it }
            }
            .toMap()

    // the feed's other all-video shelves, already turned into banners: cleaning each title builds a regex, which hitched the frame the row scrolled in
    fun feedVideoRows(page: HomePage?): Map<Int, List<HomeVideo>> =
        page?.sections.orEmpty()
            .mapIndexedNotNull { index, section ->
                if (ArtistVideos.feedShelfKind(section.title, section.items) != FeedVideoKind.Other) return@mapIndexedNotNull null
                index to section.items.filterIsInstance<SongItem>().map(ArtistVideos::feedVideo)
            }
            .toMap()

    private fun feedVideos(page: HomePage?, kinds: Map<Int, FeedVideoKind>, kind: FeedVideoKind): List<HomeVideo> =
        page?.sections.orEmpty().flatMapIndexed { index, section ->
            if (kinds[index] == kind) section.items.filterIsInstance<SongItem>().map(ArtistVideos::feedVideo) else emptyList()
        }

    // YouTube's "Live performances" and "Music videos for you" fold into Home's rows of the same
    // name, so each shows once
    fun mergeVideoRow(own: List<HomeVideo>?, page: HomePage?, kinds: Map<Int, FeedVideoKind>, kind: FeedVideoKind): List<HomeVideo>? =
        ArtistVideos.merge(own, feedVideos(page, kinds, kind))

    /**
     * Your pins first, then YouTube's speed dial; only a short one gets topped up from what you
     * play, and then from quick picks. Album tiles only come from the listening history, as they
     * always have.
     */
    fun speedDial(
        pinned: List<YTItem>,
        fromYouTube: List<YTItem>,
        keepListening: List<LocalItem>?,
        quick: List<LocalItem>?,
    ): List<YTItem> {
        val filled = (pinned + fromYouTube).distinctBy { it.id }.toMutableList()
        val targetSize = 27

        if (filled.size < targetSize) {

            keepListening?.let { k ->
                val needed = targetSize - filled.size
                val available = k.filter { item ->
                    filled.none { p -> p.id == item.id }
                }.mapNotNull { item ->
                    when (item) {
                        is Song -> SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        )
                        is Album -> AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        )
                        is Playlist -> PlaylistItem(
                            id = item.id,
                            title = item.title,
                            author = Artist(name = "Local Playlist", id = null),
                            songCountText = item.songCount.toString() + " songs",
                            thumbnail = item.thumbnails.firstOrNull() ?: "",
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        )
                        else -> null
                    }
                }
                filled.addAll(available.take(needed))
            }
        }

        if (filled.size < targetSize) {

            quick?.let { q ->
                val needed = targetSize - filled.size
                val available = q.filter { item ->
                    filled.none { p -> p.id == item.id }
                }.mapNotNull { item ->
                    when (item) {
                        is Song -> SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        )
                        is Playlist -> PlaylistItem(
                            id = item.id,
                            title = item.title,
                            author = Artist(name = "Local Playlist", id = null),
                            songCountText = item.songCount.toString() + " songs",
                            thumbnail = item.thumbnails.firstOrNull() ?: "",
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        )
                        else -> null
                    }
                }
                filled.addAll(available.take(needed))
            }
        }

        return filled
    }
}
