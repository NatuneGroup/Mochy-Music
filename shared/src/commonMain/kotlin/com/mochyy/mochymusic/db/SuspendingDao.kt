package com.mochyy.mochymusic.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.mochyy.mochymusic.db.entities.AlbumEntity
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.ImportSession
import com.mochyy.mochymusic.db.entities.ImportSessionSong
import com.mochyy.mochymusic.db.entities.ImportedPlaylist
import com.mochyy.mochymusic.db.entities.ImportedPlaylistTrack
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEdit
import com.mochyy.mochymusic.db.entities.SongEntity
import kotlinx.datetime.LocalDateTime

// Suspend versions of the app DatabaseDao's blocking members, for code that already runs in a coroutine
// and for the shared database later (Room on desktop and iOS only runs suspend or Flow functions).
// Same SQL, conflict strategy and @Transaction as the blocking original of the same name. They live in
// their own DAO because a suspend function can't share a name and parameters with a blocking one in
// DatabaseDao. Once the blocking ones are gone, DatabaseDao can extend this.
// Already covered elsewhere in shared: getSongByIdBlocking = SongDao.getSongById,
// songEditBlocking = SongDao.songEdit(..).first(), getImportedPlaylist = PlaylistDao.importedPlaylist(..).first().
@Dao
interface SuspendingDao {
    // ---- songs and artists ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(map: SongArtistMap)

    @Update
    suspend fun update(song: SongEntity)

    @Update
    suspend fun update(artist: ArtistEntity)

    @Update
    suspend fun update(album: AlbumEntity)

    @Upsert
    suspend fun upsert(edit: SongEdit)

    @Upsert
    suspend fun upsert(lyrics: LyricsEntity)

    @Delete
    suspend fun delete(songArtistMap: SongArtistMap)

    @Delete
    suspend fun delete(artist: ArtistEntity)

    @Transaction
    @Query("SELECT * FROM song_artist_map WHERE songId = :songId")
    suspend fun songArtistMap(songId: String): List<SongArtistMap>

    @Transaction
    @Query("SELECT * FROM artist WHERE name = :name")
    suspend fun artistByName(name: String): ArtistEntity?

    @Query("SELECT * FROM artist WHERE id = :id LIMIT 1")
    suspend fun getArtistById(id: String): ArtistEntity?

    @Query(
        "SELECT (SELECT COUNT(*) FROM song_artist_map WHERE artistId = :artistId) + " +
            "(SELECT COUNT(*) FROM album_artist_map WHERE artistId = :artistId)"
    )
    suspend fun artistUses(artistId: String): Int

    @Query("UPDATE song SET genre = :genre WHERE id = :songId")
    suspend fun setSongGenre(songId: String, genre: String)

    @Query("UPDATE song SET isDownloaded = :downloaded, dateDownload = :date WHERE id = :songId")
    suspend fun updateDownloadedInfo(songId: String, downloaded: Boolean, date: LocalDateTime?)

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + :playTime WHERE id = :songId")
    suspend fun incrementTotalPlayTime(songId: String, playTime: Long)

    @Transaction
    @Query("UPDATE song SET inLibrary = :inLibrary, addedByLike = 0 WHERE id = :songId")
    suspend fun inLibrary(
        songId: String,
        inLibrary: LocalDateTime?,
    )

    @Transaction
    @Query("SELECT COUNT(1) FROM related_song_map WHERE songId = :songId LIMIT 1")
    suspend fun hasRelatedSongs(songId: String): Boolean

    @Query("DELETE FROM lyrics WHERE provider NOT IN (:keepProviders)")
    suspend fun clearFetchedLyrics(keepProviders: List<String>): Int

    @Query("SELECT localUri FROM song WHERE localUri IS NOT NULL")
    suspend fun localUris(): List<String>

    @Query(
        "SELECT (SELECT COUNT(*) FROM song WHERE thumbnailUrl = :url) + (SELECT COUNT(*) FROM album WHERE thumbnailUrl = :url) + " +
            "(SELECT COUNT(*) FROM playlist WHERE thumbnailUrl = :url) + (SELECT COUNT(*) FROM artist WHERE thumbnailUrl = :url)"
    )
    suspend fun thumbnailUsers(url: String): Int

    // ---- library upkeep ----

    @Query(
        "INSERT OR IGNORE INTO album (id, title, thumbnailUrl, songCount, duration, lastUpdateTime) " +
            "SELECT albumId, albumName, thumbnailUrl, 0, 0, :now FROM song " +
            "WHERE (inLibrary IS NOT NULL OR liked) AND albumId IS NOT NULL AND albumName IS NOT NULL AND albumName != '' " +
            "GROUP BY albumId"
    )
    suspend fun addMissingAlbumRows(now: LocalDateTime)

    @Query(
        "INSERT OR IGNORE INTO album_artist_map (albumId, artistId, `order`) " +
            "SELECT song.albumId, song_artist_map.artistId, 0 FROM song " +
            "JOIN song_artist_map ON song_artist_map.songId = song.id AND song_artist_map.position = 0 " +
            "JOIN album ON album.id = song.albumId " +
            "WHERE (song.inLibrary IS NOT NULL OR song.liked) " +
            "AND NOT EXISTS(SELECT 1 FROM album_artist_map WHERE album_artist_map.albumId = song.albumId) " +
            "GROUP BY song.albumId"
    )
    suspend fun addMissingAlbumArtists()

    @Transaction
    suspend fun addMissingLibraryAlbums() {
        addMissingAlbumRows(nowLocal())
        addMissingAlbumArtists()
    }

    @Query("DELETE FROM song_artist_map WHERE artistId IN (SELECT id FROM artist WHERE name GLOB '[0-9]*:[0-9][0-9]')")
    suspend fun unlinkTimestampArtists(): Int

    @Query("DELETE FROM artist WHERE name GLOB '[0-9]*:[0-9][0-9]'")
    suspend fun deleteTimestampArtists(): Int

    @Transaction
    suspend fun purgeTimestampArtists(): Int {
        unlinkTimestampArtists()
        return deleteTimestampArtists()
    }

    // ---- playlists ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(map: PlaylistSongMap)

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlist WHERE id = :playlistId")
    suspend fun playlistEntity(playlistId: String): PlaylistEntity?

    @Query("SELECT MAX(id) FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun lastPlaylistEntryId(playlistId: String): Long?

    @Query("UPDATE playlist SET thumbnailUrl = :thumbnailUrl WHERE id = :playlistId AND thumbnailUrl IS :expected")
    suspend fun replacePlaylistCover(playlistId: String, expected: String?, thumbnailUrl: String): Int

    @Query("SELECT songId from playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds)")
    suspend fun playlistDuplicates(
        playlistId: String,
        songIds: List<String>,
    ): List<String>

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds) AND position >= :fromPosition")
    suspend fun removeAddedSongs(playlistId: String, songIds: List<String>, fromPosition: Int)

    @Transaction
    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: String)

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId AND position >= :from ORDER BY position")
    suspend fun playlistSongMaps(
        playlistId: String,
        from: Int,
    ): List<PlaylistSongMap>

    @Transaction
    suspend fun addSongToPlaylist(playlist: Playlist, songIds: List<String>) {
        var position = playlist.songCount
        songIds.forEach { id ->
            insert(
                PlaylistSongMap(
                    songId = id,
                    playlistId = playlist.id,
                    position = position++
                )
            )
        }
    }

    // ---- imported music and import sessions ----

    @Insert
    suspend fun insert(session: ImportSession): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: ImportSessionSong)

    @Query("SELECT DISTINCT songId FROM imported_song")
    suspend fun importedSongIds(): List<String>

    @Query("SELECT DISTINCT songId FROM imported_song WHERE source != :source")
    suspend fun importedSongIdsExcept(source: String): List<String>

    @Query("DELETE FROM imported_song WHERE songId = :songId AND source = :source")
    suspend fun deleteImportedSong(songId: String, source: String)

    @Query("UPDATE import_session_song SET liked = 1 WHERE sessionId = :sessionId AND songId = :songId")
    suspend fun markLikedByImport(sessionId: Long, songId: String)

    @Query("SELECT * FROM import_session WHERE source = :source ORDER BY id DESC")
    suspend fun importSessions(source: String): List<ImportSession>

    @Query("SELECT * FROM import_session WHERE source != :excluded ORDER BY id DESC")
    suspend fun importSessionsExcept(excluded: String): List<ImportSession>

    @Query("SELECT * FROM import_session_song WHERE sessionId = :sessionId")
    suspend fun importSessionSongs(sessionId: Long): List<ImportSessionSong>

    @Query("SELECT DISTINCT songId FROM import_session_song")
    suspend fun importSessionSongIds(): List<String>

    @Query("SELECT COUNT(*) FROM import_session_song WHERE songId = :songId AND sessionId != :sessionId")
    suspend fun otherSessionsWithSong(songId: String, sessionId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM import_session_song JOIN import_session ON import_session.id = import_session_song.sessionId " +
            "WHERE import_session_song.songId = :songId AND import_session.source = :source AND import_session.id != :sessionId"
    )
    suspend fun otherSessionsFromSource(songId: String, source: String, sessionId: Long): Int

    @Query("DELETE FROM import_session_song WHERE sessionId = :sessionId")
    suspend fun deleteImportSessionSongs(sessionId: Long)

    @Query("DELETE FROM import_session WHERE id = :sessionId")
    suspend fun deleteImportSession(sessionId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM import_session WHERE id = :sessionId)")
    suspend fun importSessionExists(sessionId: Long): Boolean

    @Query(
        "DELETE FROM import_session WHERE id = :sessionId " +
            "AND NOT EXISTS (SELECT 1 FROM import_session_song WHERE sessionId = :sessionId) " +
            "AND NOT EXISTS (SELECT 1 FROM imported_playlist WHERE sessionId = :sessionId)"
    )
    suspend fun deleteImportSessionIfEmpty(sessionId: Long)

    @Query("SELECT * FROM imported_playlist WHERE source = :source AND remoteId = :remoteId ORDER BY importedAt DESC LIMIT 1")
    suspend fun importedPlaylistByRemoteId(source: String, remoteId: String): ImportedPlaylist?

    @Query(
        "UPDATE imported_playlist SET remoteId = COALESCE(:remoteId, remoteId), " +
            "remoteUrl = COALESCE(:remoteUrl, remoteUrl), lastSyncedAt = :syncedAt, " +
            "authorName = COALESCE(:authorName, authorName), authorAvatarUrl = COALESCE(:authorAvatarUrl, authorAvatarUrl), " +
            "authorUrl = COALESCE(:authorUrl, authorUrl) WHERE playlistId = :playlistId"
    )
    suspend fun updateImportedPlaylistOrigin(
        playlistId: String,
        remoteId: String?,
        remoteUrl: String?,
        syncedAt: LocalDateTime,
        authorName: String?,
        authorAvatarUrl: String?,
        authorUrl: String?,
    )

    @Query("SELECT trackKey FROM imported_playlist_track WHERE playlistId = :playlistId")
    suspend fun importedPlaylistTrackKeys(playlistId: String): List<String>

    @Query("SELECT COUNT(*) FROM imported_playlist_track WHERE playlistId = :playlistId")
    suspend fun importedPlaylistTrackCount(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImportedPlaylistTracks(tracks: List<ImportedPlaylistTrack>)

    @Query("SELECT playlistId FROM imported_playlist WHERE sessionId = :sessionId")
    suspend fun importSessionPlaylistIds(sessionId: Long): List<String>

    @Query("DELETE FROM imported_playlist WHERE playlistId = :playlistId")
    suspend fun deleteImportedPlaylistRow(playlistId: String)

    @Query("DELETE FROM imported_playlist_track WHERE playlistId = :playlistId")
    suspend fun deleteImportedPlaylistTracks(playlistId: String)

    @Transaction
    suspend fun deleteImportedPlaylist(playlistId: String) {
        deleteImportedPlaylistRow(playlistId)
        deleteImportedPlaylistTracks(playlistId)
    }
}
