// ImportModels.kt

package com.mochyy.mochymusic.importer

import com.music.innertube.models.SongItem

/** one re-sync run: the playlist being read now, and what the run has done so far */
data class ResyncSummary(
    val playlistName: String,
    /** how many playlists the run re-syncs (re-sync all), and how many are finished */
    val playlists: Int = 1,
    val done: Int = 0,
    /** new songs added over every playlist so far, and how many playlists got any */
    val added: Int = 0,
    val playlistsChanged: Int = 0,
    /** playlists that couldn't be read or added to */
    val failed: List<String> = emptyList(),
    /** something to know about how one was read, like a link that only shares part of the playlist */
    val note: String? = null,
)

/** a re-sync's read of a playlist: its songs on the service now, and where it was found */
data class ResyncRead(
    val tracks: List<ImportedTrack>,
    val origin: PlaylistOrigin,
    val note: String? = null,
)

/** one playlist for MusicImportService to re-sync, read runs inside the re-sync so the wait for the service already shows as progress */
class ResyncRequest(
    val playlistId: String,
    val playlistName: String,
    val source: ImportSource,
    val read: suspend () -> ResyncRead,
)

data class ImportOptions(
    /** also create the playlists (and likes) on the signed-in youtube account */
    val mirrorToYouTube: Boolean = false,
)

/** a track that couldn't be matched, with where it came from so the user can find it by hand */
data class UnmatchedTrack(
    val track: ImportedTrack,
    val playlistName: String?,
)

data class ImportProgress(
    val isRunning: Boolean = false,
    val totalTracks: Int = 0,
    val processedTracks: Int = 0,
    val matchedTracks: Int = 0,
    val currentLabel: String = "",
    val isDone: Boolean = false,
    val cancelled: Boolean = false,
    /** the service being imported from, for the "adding 12 of 340 tracks from spotify" banner */
    val source: ImportSource? = null,
    val playlistsCreated: Int = 0,
    val likedAdded: Int = 0,
    val unmatched: List<UnmatchedTrack> = emptyList(),
    /** playlists that imported locally but couldn't be created or filled on youtube */
    val youtubeFailures: List<String> = emptyList(),
    val error: String? = null,
    /** set while this run is a re-sync of one playlist rather than an import */
    val resync: ResyncSummary? = null,
)

/** a youtube song needs no matching: the id is exact, and the full item is kept */
internal fun SongItem.toImportedTrack() = ImportedTrack(
    title = title,
    artist = artists.joinToString(", ") { it.name },
    album = album?.name,
    durationMs = duration?.times(1000L),
    videoId = id,
    thumbnailUrl = thumbnail,
    ytItem = this,
)
