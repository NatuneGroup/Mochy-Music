// YouTubePlaylistMirror.kt

package com.mochyy.mochymusic.importer

import com.mochyy.mochymusic.db.MochyDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** creates a youtube copy of a local playlist and links the two, so later edits reach both */
object YouTubePlaylistMirror {

    /** true when the playlist exists on youtube with all its (youtube) songs added */
    suspend fun mirror(database: MochyDatabase, transactions: ImportTransactions, playlistId: String, name: String): Boolean {
        val newBrowseId = runCatching { ImporterYouTube.createPlaylist(name) }.getOrNull()?.getOrNull() ?: return false
        val current = database.playlistDao.playlist(playlistId).first()?.playlist ?: return false
        database.suspendingDao.update(current.copy(browseId = newBrowseId))

        val ids = database.playlistDao.playlistSongs(playlistId).first()
            .sortedBy { it.map.position }
            // songs from files on this phone have no youtube video to add
            .filterNot { it.song.song.isLocal }
            .map { it.map.songId }
        if (ids.isEmpty()) return true
        val addedPairs = ImporterYouTube.addVideosToPlaylistWithSetVideoIds(newBrowseId, ids).getOrNull() ?: return false
        if (addedPairs.isNotEmpty()) {
            val queues = addedPairs.groupBy({ it.first }, { it.second }).mapValues { ArrayDeque(it.value) }
            val maps = database.playlistDao.playlistSongs(playlistId).first().map { it.map }.sortedBy { it.position }
            transactions.run {
                maps.forEach { map ->
                    if (map.setVideoId == null) {
                        queues[map.songId]?.removeFirstOrNull()?.let { database.suspendingDao.update(map.copy(setVideoId = it)) }
                    }
                }
            }
        } else {
            attachSetVideoIds(database, transactions, playlistId, newBrowseId)
        }
        return true
    }

    /** removing a song from a youtube playlist needs its per-entry setVideoId, which only exists once youtube has the songs, fill them in without dropping any local entry */
    private suspend fun attachSetVideoIds(database: MochyDatabase, transactions: ImportTransactions, playlistId: String, browseId: String) {
        delay(1500)
        val remote = ImporterYouTube.playlist(browseId).getOrNull()?.songs ?: return
        val queues = remote.filter { it.setVideoId != null }
            .groupBy({ it.id }, { it.setVideoId!! })
            .mapValues { ArrayDeque(it.value) }
        if (queues.isEmpty()) return
        val maps = database.playlistDao.playlistSongs(playlistId).first().map { it.map }.sortedBy { it.position }
        transactions.run {
            maps.forEach { map ->
                if (map.setVideoId == null) {
                    queues[map.songId]?.removeFirstOrNull()?.let { database.suspendingDao.update(map.copy(setVideoId = it)) }
                }
            }
        }
    }
}
