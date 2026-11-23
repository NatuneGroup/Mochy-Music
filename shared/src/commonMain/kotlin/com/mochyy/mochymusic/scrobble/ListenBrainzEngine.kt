// ListenBrainzEngine.kt
//
// ListenBrainz scrobbling, ported from the design in the screenshots: a user token in the
// settings, a playing-now the moment a song starts, and a finished listen whenever a track
// actually ran to its end — the natural end of the song or a repeat, not a skip. No timing
// rules of its own: Last.fm's min-duration and delay settings belong to the Last.fm services,
// while ListenBrainz takes the listen itself, played through.

package com.mochyy.mochymusic.scrobble

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.constants.ListenBrainzEnabledKey
import com.mochyy.mochymusic.constants.ListenBrainzPrimaryArtistOnlyKey
import com.mochyy.mochymusic.constants.ListenBrainzTokenKey
import com.music.lastfm.audioscrobblerHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mochyy.mochymusic.db.nowEpochMillis
import com.mochyy.mochymusic.utils.ioDispatcher

/** the song the listens are about, in the parts ListenBrainz wants */
data class ListenBrainzTrack(
    val id: String,
    val title: String,
    val artists: List<String>,
    val albumTitle: String?,
)

/** the one endpoint both listen types go to */
private const val SubmitListensUrl = "https://api.listenbrainz.org/1/submit-listens"

/** the earliest listened_at the API accepts (2002-10-01); anything before reads as now */
private const val MinListenTimestamp = 1_033_430_400L

class ListenBrainzEngine(
    private val preferences: DataStore<Preferences>,
    private val isOffline: () -> Boolean,
    /** BuildConfig.APPLICATION_ID on Android: what the listens say they were sent with */
    private val applicationId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val client by lazy { audioscrobblerHttpClient() }

    /** the song the listens are about, and when it started */
    private var song: ListenBrainzTrack? = null
    private var startMs = 0L
    private var durationMs = 0L

    /** whether the settings turn it on; read fresh with every call so the page's switch is the whole truth */
    private suspend fun active(): Boolean {
        if (isOffline()) return false
        val prefs = preferences.data.first()
        return prefs[ListenBrainzEnabledKey] == true && !prefs[ListenBrainzTokenKey].isNullOrBlank()
    }

    private suspend fun primaryArtistOnly(): Boolean =
        preferences.data.first()[ListenBrainzPrimaryArtistOnlyKey] == true

    /** the token, or nothing when the page's field is empty */
    private suspend fun token(): String? =
        preferences.data.first()[ListenBrainzTokenKey]?.takeIf { it.isNotBlank() }

    /**
     * A new song began. Sent as the playing-now, and kept with its start time for the finished
     * listen it becomes if it runs to its end.
     */
    fun onSongStart(track: ListenBrainzTrack?, duration: Long, playing: Boolean) {
        val started = track ?: return
        scope.launch {
            if (!active()) return@launch
            song = started
            durationMs = duration
            startMs = if (playing) nowEpochMillis() else 0L
            submit(
                listenType = "playing_now",
                listenedAt = null,
                track = started,
                positionMs = 0L,
            )
        }
    }

    /**
     * The song changed. [ended] says the last one ran to its end — the natural end or a repeat —
     * which is the only thing that finishes a listen; a skip means it never happened.
     */
    fun onSongChanged(ended: Boolean) {
        val finished = song
        val finishedStart = startMs
        val finishedDuration = durationMs
        song = null
        startMs = 0L
        durationMs = 0L
        if (finished == null || !ended || finishedStart <= 0L) return
        scope.launch {
            if (!active()) return@launch
            submit(
                listenType = "single",
                listenedAt = finishedStart / 1000L,
                track = finished,
                positionMs = finishedDuration,
            )
        }
    }

    /** playback reached the end of the queue; the song standing there is done */
    fun onPlaybackEnded() = onSongChanged(ended = true)

    /** a pause that outlives the process: send what was standing, best effort, before the end */
    fun onServiceDestroyed() {
        val finished = song
        val finishedStart = startMs
        val finishedDuration = durationMs
        song = null
        startMs = 0L
        durationMs = 0L
        if (finished == null || finishedStart <= 0L) return
        // the service's scope is being cancelled, so this runs on its own
        val lastChance = CoroutineScope(ioDispatcher)
        lastChance.launch {
            try {
                if (!active()) return@launch
                submit(
                    listenType = "single",
                    listenedAt = finishedStart / 1000L,
                    track = finished,
                    positionMs = finishedDuration,
                )
            } finally {
                lastChance.cancel()
            }
        }
    }

    /** keeps the start time honest across pauses, and the duration once the player knows it */
    fun onPlayingChanged(track: ListenBrainzTrack?, duration: Long, playing: Boolean) {
        val standing = song ?: return
        if (track != null && track.id != standing.id) return
        if (duration > 0L) durationMs = duration
        if (playing && startMs == 0L) startMs = nowEpochMillis()
    }

    /** forgets what was standing; the queue changed under it */
    fun reset() {
        song = null
        startMs = 0L
        durationMs = 0L
    }

    private suspend fun submit(
        listenType: String,
        listenedAt: Long?,
        track: ListenBrainzTrack,
        positionMs: Long,
    ) {
        val apiToken = token() ?: return
        val primaryOnly = primaryArtistOnly()
        val artist = if (primaryOnly) primaryArtist(track) else track.artists.joinToString(", ")
        val release = track.albumTitle?.takeIf { it.isNotBlank() }
        val durationPart = if (positionMs > 0L) "\"duration_ms\":${positionMs}," else ""
        val releasePart = if (release != null) "\"release_name\":\"${escapeJson(release)}\"," else ""
        val listenedAtPart = if (listenedAt != null) {
            "\"listened_at\":${listenedAt.coerceAtLeast(MinListenTimestamp)},"
        } else {
            ""
        }
        val trackMetadata =
            "{$listenedAtPart\"track_metadata\":{" +
                "\"artist_name\":\"${escapeJson(artist)}\"," +
                "\"track_name\":\"${escapeJson(track.title)}\"," +
                "$releasePart" +
                "\"additional_info\":{$durationPart\"submission_client\":\"${applicationId}\"}}}"
        val body = "{\"listen_type\":\"$listenType\",\"payload\":[$trackMetadata]}"
        try {
            val response = client.post(SubmitListensUrl) {
                header("Authorization", "Token $apiToken")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            // read the body so the connection is finished with either way
            response.bodyAsText()
            if (!response.status.isSuccess()) {
                println("ListenBrainz: submit $listenType answered HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            println("ListenBrainz: submit $listenType failed (${e.message})")
        }
    }

    /** the lead artist of a joint credit, the way the Last.fm services read it */
    private fun primaryArtist(track: ListenBrainzTrack): String =
        track.artists.firstOrNull()?.trim().orEmpty().ifEmpty {
            track.artists.joinToString(", ")
        }

    private fun escapeJson(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}
