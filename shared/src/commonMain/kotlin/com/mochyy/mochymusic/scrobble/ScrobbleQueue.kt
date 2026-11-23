// ScrobbleQueue.kt

package com.mochyy.mochymusic.scrobble

import com.music.lastfm.ScrobbleTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.mochyy.mochymusic.db.nowEpochMillis

/** A play waiting to be sent, and the services that still need it. */
@Serializable
internal data class PendingScrobble(
    val track: ScrobbleTrack,
    /** When it started playing, in seconds since 1970. */
    val startedAt: Long,
    val sites: Set<String>,
)

/**
 * Where the queue's file lives. Android writes it with AtomicFile, desktop and iOS with their own
 * file APIs; the queue itself only reads and writes whole files, so it never needs a path.
 */
interface ScrobbleQueueStore {
    /** The file's bytes, or null when it doesn't exist. */
    suspend fun read(): ByteArray?

    /** Replaces the file's contents whole. */
    suspend fun write(bytes: ByteArray)
}

/**
 * Plays waiting to be sent, kept in a file so they outlast the app closing, a reboot, or a week
 * with no signal. Each play remembers which services still need it, so one service being down
 * doesn't hold back the other.
 */
internal class ScrobbleQueue(private val store: ScrobbleQueueStore) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PendingScrobble.serializer())

    /** Read from the file on first use, then kept here. */
    private var plays: MutableList<PendingScrobble>? = null

    private val _size = MutableStateFlow(0)

    /** How many plays are still waiting for at least one service. */
    val size: StateFlow<Int> = _size.asStateFlow()

    /** Reads the file, so [size] is right before anything has been played. */
    suspend fun load() = edit { }

    suspend fun add(play: PendingScrobble) = edit { plays ->
        plays += play
        // weeks offline with music on all day stays well under this; it only stops a runaway file
        while (plays.size > MAX_PLAYS) plays.removeAt(0)
    }

    /** The oldest plays [site] still needs, up to [max]. Plays too old for any service to take are dropped. */
    suspend fun nextFor(site: String, max: Int): List<PendingScrobble> = edit { plays ->
        val oldest = nowEpochMillis() / 1000 - MAX_AGE_SECONDS
        plays.removeAll { it.startedAt < oldest }
        plays.filter { site in it.sites }.take(max)
    }

    /** [sent] reached [site]. A play no other service is waiting for is done with. */
    suspend fun markSent(site: String, sent: List<PendingScrobble>) = edit { plays ->
        // by play rather than by object: the other service may have been marked in between
        val done = sent.mapTo(HashSet()) { it.startedAt to it.track }
        plays.replaceAll { if ((it.startedAt to it.track) in done) it.copy(sites = it.sites - site) else it }
        plays.removeAll { it.sites.isEmpty() }
    }

    /** Signed out of [site]: nothing waiting for it can be sent any more. */
    suspend fun forget(site: String) = edit { plays ->
        plays.replaceAll { it.copy(sites = it.sites - site) }
        plays.removeAll { it.sites.isEmpty() }
    }

    private suspend fun <T> edit(change: (MutableList<PendingScrobble>) -> T): T = mutex.withLock {
        val plays = this.plays ?: read().also { this.plays = it }
        val before = plays.toList()
        val result = change(plays)
        if (plays != before) write(plays)
        _size.value = plays.size
        result
    }

    private suspend fun read(): MutableList<PendingScrobble> = try {
        val bytes = store.read()
        if (bytes == null) mutableListOf() else json.decodeFromString(serializer, bytes.decodeToString()).toMutableList()
    } catch (e: Exception) {
        // a file cut short when the phone died mid-write: better to start empty than never scrobble again
        logProblem("Couldn't read the scrobble queue, starting it again", e)
        mutableListOf()
    }

    private suspend fun write(plays: List<PendingScrobble>) {
        try {
            store.write(json.encodeToString(serializer, plays).encodeToByteArray())
        } catch (e: Exception) {
            logProblem("Couldn't save the scrobble queue", e)
        }
    }

    private fun logProblem(message: String, error: Throwable) = println("$LOG_TAG: $message (${error.message})")

    private companion object {
        const val LOG_TAG = "Scrobble"
        const val MAX_PLAYS = 5000

        /** Last.fm turns away scrobbles more than two weeks old. */
        const val MAX_AGE_SECONDS = 14L * 24 * 60 * 60
    }
}
