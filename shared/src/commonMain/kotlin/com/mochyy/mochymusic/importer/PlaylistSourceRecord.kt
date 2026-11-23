// PlaylistSourceRecord.kt

package com.mochyy.mochymusic.importer

import com.mochyy.mochymusic.db.MochyDatabase
import com.mochyy.mochymusic.db.entities.ImportedPlaylist
import com.mochyy.mochymusic.db.entities.ImportedPlaylistTrack
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.db.nowLocal
import kotlinx.coroutines.flow.first

/** what mochy remembers about an imported playlist on its service: where it lives and every song it had last time, so reading it again only brings in what is new */
class PlaylistSourceRecord(
    private val database: MochyDatabase,
    private val transactions: ImportTransactions,
) {

    /** the copy an earlier import made of this playlist: same place on source, or else one with its name (old imports, mochy backups), a copy tied to a different playlist is never it */
    suspend fun existingCopy(source: ImportSource, name: String, origin: PlaylistOrigin?): PlaylistEntity? {
        origin?.remoteId
            ?.let { database.suspendingDao.importedPlaylistByRemoteId(source.key, it) }
            ?.let { database.playlistDao.playlist(it.playlistId).first()?.playlist }
            ?.let { return it }
        return database.playlistDao.playlistsByNameAsc().first()
            .filter { it.playlist.name == name && it.playlist.isEditable }
            .firstOrNull { candidate ->
                val tied = database.playlistDao.importedPlaylist(candidate.id).first()
                origin?.remoteId == null || tied?.source != source.key || tied.remoteId == null
            }
            ?.playlist
    }

    /** the tracks playlistId hasn't had from the service before, each once, older playlists only have their songs so those are paired by title and artist */
    suspend fun newTracks(playlistId: String, tracks: List<ImportedTrack>): List<ImportedTrack> {
        val known = database.suspendingDao.importedPlaylistTrackKeys(playlistId).toHashSet()
        if (known.isEmpty()) {
            database.playlistDao.playlistSongs(playlistId).first().forEach { entry ->
                val title = TrackMatcher.normalizeTitle(entry.song.song.title)
                known += youTubeKey(entry.song.id)
                entry.song.artists.forEach { known += "$title|${TrackMatcher.normalizeText(it.name)}" }
            }
        }
        val seen = HashSet<String>()
        return tracks.filter { track ->
            val key = track.trackKey()
            key !in known && seen.add(key)
        }
    }

    /** saves where the playlist lives on source, who made it there, and every song it has there now */
    suspend fun remember(source: ImportSource, playlistId: String, tracks: List<ImportedTrack>, origin: PlaylistOrigin?) {
        // one made here, or from another service, that an import topped up by name: nothing to re-sync with
        if (database.playlistDao.importedPlaylist(playlistId).first()?.source != source.key) return
        transactions.run {
            val author = origin?.author
            database.suspendingDao.updateImportedPlaylistOrigin(
                playlistId, origin?.remoteId, origin?.remoteUrl, nowLocal(),
                author?.name, author?.avatarUrl, author?.profileUrl,
            )
            database.suspendingDao.insertImportedPlaylistTracks(tracks.map { ImportedPlaylistTrack(playlistId, it.trackKey()) }.distinct())
        }
    }

    /** a youtube playlist saved as a copy from its own page, no import owned it so this makes it one re-sync can read again by youTubeId */
    suspend fun rememberYouTubeCopy(playlistId: String, youTubeId: String, songIds: List<String>) {
        val now = nowLocal()
        transactions.run {
            database.suspendingDao.insert(
                ImportedPlaylist(
                    playlistId = playlistId,
                    source = ImportSource.YOUTUBE_MUSIC.key,
                    sessionId = null,
                    importedAt = now,
                    remoteId = youTubeId,
                    remoteUrl = "https://music.youtube.com/playlist?list=$youTubeId",
                    lastSyncedAt = now,
                ),
            )
            database.suspendingDao.insertImportedPlaylistTracks(songIds.distinct().map { ImportedPlaylistTrack(playlistId, youTubeKey(it)) })
        }
    }
}
