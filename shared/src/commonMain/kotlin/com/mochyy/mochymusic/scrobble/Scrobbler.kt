// Scrobbler.kt

package com.mochyy.mochymusic.scrobble

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.constants.LastFMUseNowPlaying
import com.mochyy.mochymusic.constants.LastFMUseSendLikes
import com.music.lastfm.Audioscrobbler
import com.music.lastfm.AudioscrobblerException
import com.music.lastfm.ScrobbleTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.mochyy.mochymusic.utils.ioDispatcher

/**
 * Sends what's played to Last.fm and Libre.fm. Every play is saved to [ScrobbleQueue] first and
 * sent from there, so nothing is lost offline: what's left is tried again once there's a network,
 * on Android by WorkManager if the app is closed by then. Now Playing goes straight out and is
 * never queued, since it means nothing once the song is over.
 *
 * The logic is the same on every platform; the pieces it can't own live behind [ScrobbleAccount],
 * [ScrobbleQueueStore], [ScrobbleRetry], [ScrobbleNotices] and [isOffline], which the platform
 * hands in.
 */
class Scrobbler(
    private val preferences: DataStore<Preferences>,
    /** where the waiting plays are kept, so they outlast the app closing */
    store: ScrobbleQueueStore,
    private val accounts: List<ScrobbleAccount>,
    private val isOffline: () -> Boolean,
    private val retry: ScrobbleRetry,
    private val notices: ScrobbleNotices,
) {
    private val queue = ScrobbleQueue(store)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val sending = Mutex()

    /** Plays still waiting to be sent, for the settings page. */
    val pending: StateFlow<Int> get() = queue.size

    init {
        // plays left from last time go out as soon as the app is back
        scope.launch {
            queue.load()
            if (queue.size.value > 0) send()
        }
    }

    fun nowPlaying(track: ScrobbleTrack) {
        if (isOffline()) return
        scope.launch {
            if (preferences.data.first()[LastFMUseNowPlaying] == false) return@launch
            for ((site, session) in sites(includePaused = false)) {
                try {
                    site.api.updateNowPlaying(session, track)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // nothing to keep: by the next try it's another song
                    if (e is AudioscrobblerException && e.isSignedOut) signedOut(site)
                    logD("${site.label} Now Playing didn't go through", e)
                }
            }
        }
    }

    /** A play that counted, which started at [startedAt] (seconds since 1970). */
    fun scrobble(track: ScrobbleTrack, startedAt: Long) {
        scope.launch {
            val sites = sites(includePaused = false).map { it.first.siteName }.toSet()
            if (sites.isEmpty()) return@launch
            queue.add(PendingScrobble(track, startedAt, sites))
            send()
        }
    }

    /** A like here becomes a love there, when the user asked for that. */
    fun love(artist: String, track: String, love: Boolean) {
        if (isOffline() || artist.isBlank()) return
        scope.launch {
            if (preferences.data.first()[LastFMUseSendLikes] != true) return@launch
            for ((site, session) in sites(includePaused = false)) {
                try {
                    site.api.love(session, artist, track, love)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is AudioscrobblerException && e.isSignedOut) signedOut(site)
                    logD("${site.label} love didn't go through", e)
                }
            }
        }
    }

    /**
     * Sends what's waiting, a batch at a time, until each service has it all or can't take more
     * right now. False when something has to be tried again later (no network, the service down),
     * which also books that try.
     */
    suspend fun send(): Boolean = sending.withLock {
        // offline mode keeps the app off the network; the next play after it's turned off sends these
        if (isOffline()) return@withLock true
        var allSent = true
        // paused services still get plays from before the pause
        for ((site, session) in sites(includePaused = true)) {
            while (true) {
                val batch = queue.nextFor(site.siteName, Audioscrobbler.MAX_BATCH)
                if (batch.isEmpty()) break
                try {
                    site.api.scrobble(session, batch.map { it.track to it.startedAt })
                    queue.markSent(site.siteName, batch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AudioscrobblerException) {
                    when {
                        e.isSignedOut -> {
                            signedOut(site)
                            break
                        }
                        // turned down for good (bad song data): kept, it would hold back every play after it
                        e.isRejected -> {
                            logW("${site.label} turned down ${batch.size} scrobbles", e)
                            queue.markSent(site.siteName, batch)
                        }
                        else -> {
                            allSent = false
                            break
                        }
                    }
                } catch (e: Exception) {
                    // no network, most likely
                    logD("${site.label} scrobbles will be sent later", e)
                    allSent = false
                    break
                }
            }
        }
        if (!allSent) retry.sendLater()
        allSent
    }

    // ---- signing in and out ----

    /** signs in from mochy's own form and keeps only the session key, the password is never stored; gives back the account's name */
    suspend fun signIn(site: ScrobbleAccount, username: String, password: String): String {
        val session = site.api.getMobileSession(username.trim(), password)
        preferences.edit {
            it[site.sessionKey] = session.key
            it[site.usernameKey] = session.name
            it[site.enabledKey] = true
        }
        return session.name
    }

    suspend fun signOut(site: ScrobbleAccount) {
        preferences.edit {
            it.remove(site.sessionKey)
            it.remove(site.usernameKey)
            it.remove(site.enabledKey)
        }
        queue.forget(site.siteName)
    }

    /** The service took back Mochy's access, so the account is forgotten here too. */
    private suspend fun signedOut(site: ScrobbleAccount) {
        signOut(site)
        notices.signedOut(site.label)
    }

    /** Signed-in services and their session keys; paused ones only with [includePaused]. */
    private suspend fun sites(includePaused: Boolean): List<Pair<ScrobbleAccount, String>> {
        val prefs = preferences.data.first()
        return accounts.mapNotNull { site ->
            val session = prefs[site.sessionKey]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!includePaused && prefs[site.enabledKey] == false) return@mapNotNull null
            site to session
        }
    }

    private fun logD(message: String, error: Exception?) = println("Scrobble: $message (${error?.message})")

    private fun logW(message: String, error: Exception?) = println("Scrobble: $message (${error?.message})")
}
