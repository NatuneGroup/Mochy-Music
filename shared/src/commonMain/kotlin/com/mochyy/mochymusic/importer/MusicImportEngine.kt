// MusicImportEngine.kt

package com.mochyy.mochymusic.importer

import com.mochyy.mochymusic.db.MochyDatabase
import com.mochyy.mochymusic.db.entities.ImportSession
import com.mochyy.mochymusic.db.nowLocal
import com.mochyy.mochymusic.importer.account.AccountImportException
import com.mochyy.mochymusic.link.PlatformLock
import com.mochyy.mochymusic.utils.ioDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One import or re-sync at a time: matches the tracks, writes the library and playlists through
 * ImportSteps, and owns the progress every screen reads. The platform's MusicImportService holds
 * one of these and hands it the app's settings, notifications, pending likes and transaction, so
 * the background run is the same object every page sees.
 */
class MusicImportEngine(
    private val database: MochyDatabase,
    private val settings: ImportSettings,
    private val notifier: ImportNotifier,
    pendingLikes: ImportPendingLikes,
    matches: ImportMatches,
    transactions: ImportTransactions,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            ImportLog.w("Music import coroutine exception", throwable)
        }
    }
    private val importJob = SupervisorJob()
    private val importScope = CoroutineScope(ioDispatcher + importJob + exceptionHandler)
    private var runningJob: Job? = null

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    /** the work itself; one import or re-sync runs at a time, so they share it */
    private val steps = ImportSteps(database, settings, transactions, _progress, matches, pendingLikes)

    /** re-syncs waiting behind the one running, and whether a re-sync run is going (guarded by the queue) */
    private val resyncQueue = ArrayDeque<ResyncRequest>()
    private val queueLock = PlatformLock()
    private var resyncing = false

    private enum class QueueState { Joined, Busy, Started }

    val isRunning: Boolean get() = runningJob?.isActive == true

    /** starts an import in the background; false if one is already running */
    fun startImport(parsed: ParsedImport, options: ImportOptions = ImportOptions()): Boolean {
        if (isRunning) return false
        steps.source = parsed.provider ?: ImportSource.OTHER
        _progress.value = ImportProgress(isRunning = true, totalTracks = parsed.totalSongs, source = parsed.provider)
        runningJob = importScope.launch {
            try {
                steps.sessionId = database.suspendingDao.insert(ImportSession(source = steps.source.key, startedAt = nowLocal()))
                steps.runImport(parsed, options)
                _progress.update { it.copy(isRunning = false, isDone = true, currentLabel = "") }
                val done = _progress.value
                notifier.importFinished(done.matchedTracks, done.totalTracks)
            } catch (e: CancellationException) {
                _progress.update { it.copy(isRunning = false, isDone = true, cancelled = true, currentLabel = "") }
                throw e
            } catch (e: Exception) {
                ImportLog.w("Import failed", e)
                _progress.update {
                    it.copy(isRunning = false, isDone = true, currentLabel = "", error = e.message ?: "Import failed")
                }
            } finally {
                steps.sessionId?.let { database.suspendingDao.deleteImportSessionIfEmpty(it) }
                steps.sessionId = null
            }
        }
        return true
    }

    /** re-reads imported playlists and adds songs new on the service to their end, ones the user removed here stay out, requests during a run join it, false while an import runs */
    fun startResync(requests: List<ResyncRequest>): Boolean {
        val first = requests.firstOrNull() ?: return true
        val queueState = queueLock.withLock {
            when {
                // a re-sync is going: these join it and the count it shows grows
                resyncing -> {
                    resyncQueue.addAll(requests)
                    _progress.update { it.copy(resync = it.resync?.copy(playlists = it.resync.playlists + requests.size)) }
                    QueueState.Joined
                }
                isRunning -> QueueState.Busy
                else -> {
                    resyncQueue.addAll(requests)
                    resyncing = true
                    QueueState.Started
                }
            }
        }
        when (queueState) {
            QueueState.Joined -> return true
            QueueState.Busy -> return false
            QueueState.Started -> Unit
        }
        _progress.value = ImportProgress(isRunning = true, source = first.source, resync = ResyncSummary(first.playlistName, playlists = requests.size))
        runningJob = importScope.launch {
            try {
                while (true) {
                    val next = queueLock.withLock {
                        resyncQueue.removeFirstOrNull().also { if (it == null) resyncing = false }
                    } ?: break
                    resyncOne(next)
                }
                _progress.update { it.copy(isRunning = false, isDone = true, currentLabel = "") }
                notifier.resyncFinished(_progress.value)
            } catch (e: CancellationException) {
                queueLock.withLock {
                    resyncQueue.clear()
                    resyncing = false
                }
                _progress.update { it.copy(isRunning = false, isDone = true, cancelled = true, currentLabel = "") }
                throw e
            }
        }
        return true
    }

    /** one playlist of a re-sync run. a playlist that fails is noted and the run goes on */
    private suspend fun resyncOne(request: ResyncRequest) {
        steps.source = request.source
        _progress.update {
            it.copy(
                source = request.source,
                currentLabel = "Reading \"${request.playlistName}\" on ${request.source.label}",
                resync = it.resync?.copy(playlistName = request.playlistName),
            )
        }
        var ownSession: Long? = null
        try {
            val read = request.read()
            val playlist = database.playlistDao.playlist(request.playlistId).first()?.playlist
                ?: throw LinkImportException("That playlist isn't in your library any more.")
            // new songs go in under the import that brought the playlist so undoing it takes them too, a playlist copied from a youtube page never put its songs in the library
            val owner = database.playlistDao.importedPlaylist(playlist.id).first()?.sessionId
            if (owner != null) {
                steps.sessionId = owner.takeIf { database.suspendingDao.importSessionExists(it) }
                    ?: database.suspendingDao.insert(ImportSession(source = steps.source.key, startedAt = nowLocal())).also { ownSession = it }
            }
            val fresh = steps.record.newTracks(playlist.id, read.tracks)
            _progress.update { it.copy(totalTracks = it.totalTracks + fresh.size) }
            val added = steps.topUp(steps.newMatcher(), playlist, fresh, settings.isUserLoggedIn(), intoLibrary = owner != null)
            steps.record.remember(steps.source, playlist.id, read.tracks, read.origin)
            _progress.update { progress ->
                progress.copy(resync = progress.resync?.let {
                    it.copy(
                        done = it.done + 1,
                        added = it.added + added.size,
                        playlistsChanged = it.playlistsChanged + if (added.isEmpty()) 0 else 1,
                        note = it.note ?: read.note,
                    )
                })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportLog.w("Re-sync of ${request.playlistName} failed", e)
            val reason = when (e) {
                is LinkImportException, is AccountImportException -> e.message
                else -> null
            } ?: "Check your connection and try again."
            _progress.update { progress ->
                progress.copy(
                    error = reason,
                    resync = progress.resync?.let { it.copy(done = it.done + 1, failed = it.failed + request.playlistName) },
                )
            }
        } finally {
            ownSession?.let { database.suspendingDao.deleteImportSessionIfEmpty(it) }
            steps.sessionId = null
        }
    }

    /** stops after the current song; everything imported so far stays */
    fun cancel() {
        runningJob?.cancel()
    }

    /** forgets a finished import's summary so the next one starts clean */
    fun clearFinished() {
        if (!isRunning) _progress.value = ImportProgress()
    }

    /** early imports made a playlist named after the service and never filled the library, file their songs into the library once and keep the playlist since it may have been edited */
    suspend fun repairEarlierImports() {
        if (isRunning) return
        val legacy = database.playlistDao.playlistsByNameAsc().first()
            .filter { it.playlist.isEditable && it.playlist.browseId == null && it.playlist.name in LegacyLibraryPlaylists }
        for (playlist in legacy) {
            val songIds = database.playlistDao.playlistSongs(playlist.id).first().map { it.song.id }
            if (songIds.isEmpty()) continue
            val tagged = database.suspendingDao.importedSongIds().toSet()
            val missing = songIds.filter { id ->
                id !in tagged || database.songDao.getSongById(id)?.song?.inLibrary == null
            }
            if (missing.isEmpty()) continue
            steps.source = LegacyLibraryPlaylists.getValue(playlist.playlist.name)
            steps.fileIntoLibrary(missing)
        }
        steps.source = ImportSource.OTHER
    }

    private companion object {
        /** what earlier versions named the playlist they made of a service's whole library */
        val LegacyLibraryPlaylists = mapOf("Apple Music library" to ImportSource.APPLE_MUSIC)
    }
}
