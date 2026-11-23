// DiscordPresence.kt
//
// Keeps the Discord status in step with the player. MusicService reports what's playing through
// [update]; this sends it to Discord when something people can see has changed, no faster than the
// user's refresh interval, and takes it down when the music stops.
//
// The status itself, the gateway, the pictures' Discord names and the rate limits are the same on
// every platform; the GIFs and uploads that need a bitmap engine come in through [DiscordPictures].

package com.mochyy.mochymusic.discord

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import com.mochyy.mochymusic.db.nowEpochMillis
import com.mochyy.mochymusic.utils.ioDispatcher

/** How the Discord status is doing, for the line under the switch. */
enum class PresenceConnection { Off, Idle, Connecting, Live, Retrying }

@OptIn(FlowPreview::class)
class DiscordPresence(
    private val preferences: DataStore<Preferences>,
    private val auth: DiscordAuth,
    private val pictures: DiscordPictures,
    private val isOffline: () -> Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val json = Json { ignoreUnknownKeys = true }

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    /** what the player last reported, for the Discord page's preview */
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    val settings: StateFlow<PresenceSettings> = preferences.data
        .map(PresenceSettings::from)
        .stateIn(scope, SharingStarted.Eagerly, PresenceSettings())

    private val _connection = MutableStateFlow(PresenceConnection.Off)
    val connection: StateFlow<PresenceConnection> = _connection.asStateFlow()

    /** goes up when something outside the flows wants the status looked at again */
    private val nudges = MutableStateFlow(0)

    /** the newest edit from the Discord page; saved one at a time, so an older one never wins */
    private val pendingSave = MutableStateFlow<PresenceSettings?>(null)

    private val gatewayLock = Mutex()
    private var gateway: DiscordGateway? = null
    @Volatile private var connectFailures = 0
    @Volatile private var nextConnectAt = 0L

    @Volatile private var lastSent: Sent? = null
    private var lastPictureRetryAt = 0L
    private val recentSends = ArrayDeque<Long>()

    // Discord's asset address for each picture address, so each one is registered only once.
    // Touched only from this class's own coroutines; the oldest entries fall out past the cap.
    private val assetPaths = LinkedHashMap<String, String>()
    private val refusedUrls = LinkedHashSet<String>()

    private data class Sent(val activity: PresenceActivity, val largeAsset: String?, val smallAsset: String?)

    init {
        scope.launch { auth.forgetOldLogin() }
        scope.launch { follow() }
        scope.launch {
            pendingSave.filterNotNull().collect { edited -> preferences.edit { edited.writeTo(it) } }
        }
    }

    fun save(edited: PresenceSettings) {
        pendingSave.value = edited
    }

    fun update(track: NowPlaying?) {
        _nowPlaying.value = track
    }

    /** Look again now, after the network came back or a spinning picture got made. */
    fun nudge() {
        nudges.update { it + 1 }
    }

    private suspend fun follow() {
        combine(auth.signedIn, settings.debounce(SettingsSettleMs), _nowPlaying, nudges) { signedIn, settings, track, _ ->
            Triple(signedIn, settings, track)
        }.collectLatest { (signedIn, settings, track) ->
            when {
                !auth.isConfigured || !signedIn || !settings.enabled -> {
                    hide()
                    _connection.value = PresenceConnection.Off
                }
                // paused with "clear status when paused" on: down at once, and playing again brings it back
                track == null || isOffline() || (!track.isPlaying && settings.clearOnPause) -> {
                    hide()
                    _connection.value = PresenceConnection.Idle
                }
                else -> show(track, settings)
            }
        }
    }

    /**
     * Keeps [track] on the profile until something changes (which cancels this). A paused song
     * stays up for a while, frozen, then comes down so the profile doesn't claim it forever.
     */
    private suspend fun show(track: NowPlaying, settings: PresenceSettings) {
        val pausedAt = if (track.isPlaying) null else track.readAtMs
        while (true) {
            val now = nowEpochMillis()
            if (pausedAt != null && now - pausedAt > KeepPausedMs) {
                hide()
                _connection.value = PresenceConnection.Idle
                return
            }
            send(PresenceActivity.build(track, settings, now), settings.refreshSeconds)
            delay(settings.refreshSeconds * 1000L)
        }
    }

    private suspend fun send(activity: PresenceActivity, refreshSeconds: Int) {
        if (!worthSending(activity, lastSent)) return

        waitForTurn(refreshSeconds)
        val live = connect() ?: return
        val token = auth.accessToken() ?: return
        val (large, small) = assetsFor(activity, token)
        val payload = activity.toJson(auth.clientId, large, small)
        if (live.sendPresence(payload)) {
            lastSent = Sent(activity, large, small)
            recentSends.addLast(nowEpochMillis())
            _connection.value = PresenceConnection.Live
        }
    }

    /**
     * The same status again isn't worth a message, unless a picture is ready now that wasn't
     * last time: a spinning one finished, or one Discord couldn't take (retried once a minute).
     */
    private fun worthSending(activity: PresenceActivity, previous: Sent?): Boolean {
        if (previous == null || !activity.looksLike(previous.activity)) return true
        if (picturesChanged(activity, previous)) return true
        val missing = (activity.largeImage != null && previous.largeAsset == null) ||
            (activity.smallImage != null && previous.smallAsset == null)
        val now = nowEpochMillis()
        if (missing && now - lastPictureRetryAt > PictureRetryMs) {
            lastPictureRetryAt = now
            // a turn-down may have been a passing hiccup on Discord's side
            refusedUrls.clear()
            return true
        }
        return false
    }

    // a spinning gif or an uploaded picture that was still being made last time may be ready now
    private fun picturesChanged(activity: PresenceActivity, previous: Sent): Boolean {
        fun readyNow(picture: PresencePicture?, sentAsset: String?): Boolean {
            if (picture !is PresencePicture.Spinning && picture !is PresencePicture.Local && picture !is PresencePicture.Canvas) return false
            // discord turned the better one down already: what it fell back to stays
            val best = candidatesFor(picture).firstOrNull { it !in refusedUrls } ?: return false
            return assetPaths[best] != sentAsset
        }
        return readyNow(activity.largeImage, previous.largeAsset) ||
            readyNow(activity.smallImage, previous.smallAsset)
    }

    /**
     * Spaces messages by the user's refresh interval, and keeps under Discord's own limit for
     * status updates (five in twenty seconds), which closes the connection when it's crossed.
     */
    private suspend fun waitForTurn(refreshSeconds: Int) {
        val now = nowEpochMillis()
        val sinceLast = recentSends.lastOrNull()?.let { now - it } ?: Long.MAX_VALUE
        val intervalWait = refreshSeconds * 1000L - sinceLast
        while (recentSends.isNotEmpty() && now - recentSends.first() > BurstWindowMs) recentSends.removeFirst()
        val burstWait = if (recentSends.size >= BurstLimit) recentSends.first() + BurstWindowMs - now else 0L
        val wait = maxOf(intervalWait, burstWait)
        if (wait > 0) delay(wait)
    }

    /** A ready connection, making one if needed. Null while Discord can't be reached. */
    private suspend fun connect(): DiscordGateway? = gatewayLock.withLock {
        gateway?.takeIf { it.isReady }?.let { return@withLock it }
        if (nowEpochMillis() < nextConnectAt) return@withLock null
        val token = auth.accessToken() ?: return@withLock null
        _connection.value = if (connectFailures == 0) PresenceConnection.Connecting else PresenceConnection.Retrying

        var opened: DiscordGateway? = null
        val fresh = DiscordGateway(token) { code -> opened?.let { onGatewayClosed(it, code, token) } }
        opened = fresh
        val ready = try {
            fresh.connect()
        } catch (e: CancellationException) {
            fresh.close()
            throw e
        }
        if (ready) {
            gateway = fresh
            connectFailures = 0
            nextConnectAt = 0
            fresh
        } else {
            connectFailures++
            nextConnectAt = nowEpochMillis() + backoffMs()
            _connection.value = PresenceConnection.Retrying
            scope.launch {
                delay(backoffMs())
                nudge()
            }
            null
        }
    }

    /** Discord or the network closed the connection: open another next time, sooner if it was a token. */
    private fun onGatewayClosed(closed: DiscordGateway, code: Int, token: String) {
        scope.launch {
            val wasCurrent = gatewayLock.withLock {
                (gateway === closed).also { if (it) gateway = null }
            }
            // a connection we closed ourselves isn't news
            if (!wasCurrent) return@launch
            lastSent = null
            log("gateway closed with $code, connecting again")
            if (code == DiscordGateway.CloseAuthFailed) auth.renew(token)
            connectFailures++
            nextConnectAt = nowEpochMillis() + backoffMs()
            _connection.value = PresenceConnection.Retrying
            delay(backoffMs())
            nudge()
        }
    }

    private suspend fun hide() {
        lastSent = null
        val current = gatewayLock.withLock { gateway.also { gateway = null } } ?: return
        // clear first: closing alone can leave the old song up for a minute
        current.sendPresence(null)
        current.close()
    }

    private fun backoffMs(): Long = Backoff[(connectFailures - 1).coerceIn(0, Backoff.lastIndex)]

    /** Hands each picture's address to Discord once and returns Discord's name for it. */
    private suspend fun assetsFor(activity: PresenceActivity, token: String): Pair<String?, String?> {
        val large = candidatesFor(activity.largeImage)
        val small = candidatesFor(activity.smallImage)
        val missing = (large + small).distinct()
            .filter { it !in refusedUrls && it !in assetPaths }
        if (missing.isNotEmpty()) register(missing, token)
        return large.firstNotNullOfOrNull { assetPaths[it] } to small.firstNotNullOfOrNull { assetPaths[it] }
    }

    /**
     * The addresses that can stand for [picture], best first. A rotating picture falls back to the
     * still artwork, while its GIF is being made or if Discord won't take the GIF's host.
     */
    private fun candidatesFor(picture: PresencePicture?): List<String> = when (picture) {
        null -> emptyList()
        is PresencePicture.Web -> listOf(picture.url)
        is PresencePicture.Status -> listOf(PresenceImages.statusUrl(picture))
        PresencePicture.Logo -> listOf(PresenceImages.LogoUrl)
        is PresencePicture.Spinning -> listOfNotNull(pictures.spinningUrl(picture.sourceUrl, picture.style), stillUrl(picture.sourceUrl))
        is PresencePicture.Local -> listOfNotNull(pictures.uploadedUrl(picture.sourceUrl))
        is PresencePicture.Canvas -> listOfNotNull(
            pictures.canvasUrl(picture.title, picture.artist, picture.durationSec, picture.source, picture.fps, picture.size),
            picture.fallback?.let(::stillUrl),
            PresenceImages.LogoUrl,
        )
    }

    // the still picture behind a spinning one: as it is from the web, uploaded first from the phone
    private fun stillUrl(source: String): String? =
        if (PresenceImages.isWebImage(source)) source else pictures.uploadedUrl(source)

    private suspend fun register(urls: List<String>, token: String, renewed: Boolean = false) {
        val body = buildJsonObject { put("urls", buildJsonArray { urls.forEach { add(JsonPrimitive(it)) } }) }
        val reply = try {
            DiscordHttp.postJson("/applications/${auth.clientId}/external-assets", token, body.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("couldn't register pictures (${e.message})")
            return
        }
        if (reply.code == 401 && !renewed) {
            auth.renew(token)?.let { register(urls, it, renewed = true) }
            return
        }
        if (!reply.ok) {
            log("pictures turned down: HTTP ${reply.code} ${reply.body.take(200)}")
            return
        }
        val registered = runCatching { json.parseToJsonElement(reply.body).jsonArray }.getOrNull() ?: return
        urls.forEachIndexed { index, url ->
            val path = registered.getOrNull(index)?.jsonObject?.get("external_asset_path")?.jsonPrimitive?.contentOrNull
            if (path != null) {
                assetPaths[url] = "mp:$path"
                // the same picture may have been refused on an earlier try
                refusedUrls.remove(url)
                while (assetPaths.size > AssetCacheSize) assetPaths.remove(assetPaths.keys.first())
            } else {
                refusedUrls.add(url)
            }
        }
    }

    private fun log(message: String) = println("DiscordPresence: $message")

    private companion object {
        /** typing in a text box shouldn't send a status per letter */
        const val SettingsSettleMs = 600L

        const val KeepPausedMs = 10 * 60_000L
        const val PictureRetryMs = 60_000L
        const val BurstLimit = 5
        const val BurstWindowMs = 20_000L

        /** how many picture addresses are remembered before the oldest is dropped */
        const val AssetCacheSize = 256

        val Backoff = longArrayOf(2_000, 5_000, 15_000, 30_000, 60_000)
    }
}
