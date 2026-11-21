// ListenBrainz scrobbling, ported from the design in the screenshots: a user token in the
// settings, a playing-now the moment a song starts, and a finished listen whenever a track
// actually ran to its end — the natural end of the song or a repeat, not a skip. No timing
// rules of its own: Last.fm's min-duration and delay settings belong to the Last.fm services,
// while ListenBrainz takes the listen itself, played through.

package com.mochyy.mochymusic.scrobble

import android.content.Context
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.constants.ListenBrainzEnabledKey
import com.mochyy.mochymusic.constants.ListenBrainzPrimaryArtistOnlyKey
import com.mochyy.mochymusic.constants.ListenBrainzTokenKey
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.utils.AppProxy
import com.mochyy.mochymusic.utils.OfflineMode
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.followAppProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** the one endpoint both listen types go to */
private const val SubmitListensUrl = "https://api.listenbrainz.org/1/submit-listens"

/** the earliest listened_at the API accepts (2002-10-01); anything before reads as now */
private const val MinListenTimestamp = 1_033_430_400L

object ListenBrainz {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .followAppProxy()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** the song the listens are about, and when it started */
    private var song: MediaMetadata? = null
    private var startMs = 0L
    private var durationMs = 0L

    /** whether the settings turn it on; read fresh with every call so the page's switch is the whole truth */
    private suspend fun active(context: Context): Boolean {
        if (OfflineMode.isOn) return false
        val prefs = context.dataStore.data.first()
        return prefs[ListenBrainzEnabledKey] == true && !prefs[ListenBrainzTokenKey].isNullOrBlank()
    }

    private suspend fun primaryArtistOnly(context: Context): Boolean =
        context.dataStore.data.first()[ListenBrainzPrimaryArtistOnlyKey] == true

    /** the token, or nothing when the page's field is empty */
    private suspend fun token(context: Context): String? =
        context.dataStore.data.first()[ListenBrainzTokenKey]?.takeIf { it.isNotBlank() }

    /**
     * A new song began. Sent as the playing-now, and kept with its start time for the finished
     * listen it becomes if it runs to its end.
     */
    fun onSongStart(context: Context, metadata: MediaMetadata?, duration: Long, playing: Boolean) {
        val started = metadata ?: return
        scope.launch {
            if (!active(context)) return@launch
            song = started
            durationMs = duration
            startMs = if (playing) System.currentTimeMillis() else 0L
            submit(
                context = context,
                listenType = "playing_now",
                listenedAt = null,
                metadata = started,
                positionMs = 0L,
            )
        }
    }

    /**
     * The song changed. [ended] says the last one ran to its end — the natural end or a repeat —
     * which is the only thing that finishes a listen; a skip means it never happened.
     */
    fun onSongChanged(context: Context, ended: Boolean) {
        val finished = song
        val finishedStart = startMs
        val finishedDuration = durationMs
        song = null
        startMs = 0L
        durationMs = 0L
        if (finished == null || !ended || finishedStart <= 0L) return
        scope.launch {
            if (!active(context)) return@launch
            submit(
                context = context,
                listenType = "single",
                listenedAt = finishedStart / 1000L,
                metadata = finished,
                positionMs = finishedDuration,
            )
        }
    }

    /** playback reached the end of the queue; the song standing there is done */
    fun onPlaybackEnded(context: Context) = onSongChanged(context, ended = true)

    /** a pause that outlives the process: send what was standing, best effort, before the end */
    fun onServiceDestroyed(context: Context) {
        val finished = song
        val finishedStart = startMs
        val finishedDuration = durationMs
        song = null
        startMs = 0L
        durationMs = 0L
        if (finished == null || finishedStart <= 0L) return
        // the service's scope is being cancelled, so this runs on its own
        val lastChance = CoroutineScope(Dispatchers.IO)
        lastChance.launch {
            try {
                if (!active(context)) return@launch
                submit(
                    context = context,
                    listenType = "single",
                    listenedAt = finishedStart / 1000L,
                    metadata = finished,
                    positionMs = finishedDuration,
                )
            } finally {
                lastChance.cancel()
            }
        }
    }

    /** keeps the start time honest across pauses, and the duration once the player knows it */
    fun onPlayingChanged(context: Context, metadata: MediaMetadata?, duration: Long, playing: Boolean) {
        val standing = song
        if (standing == null) return
        if (metadata != null && metadata.id != standing.id) return
        if (duration > 0L) durationMs = duration
        if (playing && startMs == 0L) startMs = System.currentTimeMillis()
    }

    /** forgets what was standing; the queue changed under it */
    fun reset() {
        song = null
        startMs = 0L
        durationMs = 0L
    }

    private suspend fun submit(
        context: Context,
        listenType: String,
        listenedAt: Long?,
        metadata: MediaMetadata,
        positionMs: Long,
    ) {
        val apiToken = token(context) ?: return
        val primaryOnly = primaryArtistOnly(context)
        val artist = if (primaryOnly) primaryArtist(metadata) else metadata.artists.joinToString(", ") { it.name }
        val release = metadata.album?.title?.takeIf { it.isNotBlank() }
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
                "\"track_name\":\"${escapeJson(metadata.title)}\"," +
                "$releasePart" +
                "\"additional_info\":{$durationPart\"submission_client\":\"${BuildConfig.APPLICATION_ID}\"}}}"
        val body = "{\"listen_type\":\"$listenType\",\"payload\":[$trackMetadata]}"
        try {
            val request = Request.Builder()
                .url(SubmitListensUrl)
                .addHeader("Authorization", "Token $apiToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag("ListenBrainz").w("submit %s answered HTTP %d", listenType, response.code)
                }
            }
        } catch (e: Exception) {
            Timber.tag("ListenBrainz").w(e, "submit %s failed", listenType)
        }
    }

    /** the lead artist of a joint credit, the way the Last.fm services read it */
    private fun primaryArtist(metadata: MediaMetadata): String =
        metadata.artists.firstOrNull()?.name?.trim().orEmpty().ifEmpty {
            metadata.artists.joinToString(", ") { it.name }
        }

    private fun escapeJson(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}
