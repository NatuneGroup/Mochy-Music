// ImportSongMapping.kt

package com.mochyy.mochymusic.importer

import com.mochyy.mochymusic.db.SongArtistInput
import com.mochyy.mochymusic.db.entities.SongEntity
import com.mochyy.mochymusic.ui.utils.resize
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV

/**
 * A found YouTube song as the row an import stores: the same values the app's
 * SongItem.toMediaMetadata().toSongEntity() wrote (duration -1 when unknown, the cover at 1200,
 * a video unless the track is the artist's own audio), with the artists the DAO writes beside it.
 */
internal fun SongItem.toImportSong(): Pair<SongEntity, List<SongArtistInput>> {
    val song = SongEntity(
        id = id,
        title = title,
        duration = duration ?: -1,
        thumbnailUrl = thumbnail.resize(1200, 1200),
        albumId = album?.id,
        albumName = album?.name,
        explicit = explicit,
        libraryAddToken = libraryAddToken,
        libraryRemoveToken = libraryRemoveToken,
        isVideo = musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV,
    )
    val artistInputs = artists.map { SongArtistInput(id = it.id, name = it.name) }
    return song to artistInputs
}
