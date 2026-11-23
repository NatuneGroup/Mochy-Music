package com.mochyy.mochymusic.shared.ios

import com.mochyy.mochymusic.shared.library.LocalLibrary
import com.mochyy.mochymusic.shared.playback.PlayerController
import com.mochyy.mochymusic.shared.playback.PlayerState
import com.mochyy.mochymusic.shared.playback.SharedQueue
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol

/** Swift calls this when iOS creates the CarPlay scene, even when the phone UI hasn't opened. */
fun installIosCarPlayBridge() {
    IosCarPlayBridge.install()
}

/** Notification bridge that gives CarPlay access to the same local library, queue and AVPlayer as Compose. */
private object IosCarPlayBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }
    private val observers = mutableListOf<NSObjectProtocol>()
    private val requestedLimits = mutableMapOf("saved" to 100, "recent" to 100)
    private var installed = false
    private var library: LocalLibrary? = null
    private var player: PlayerController? = null
    private var queue: SharedQueue? = null
    private var savedCollector: Job? = null
    private var recentCollector: Job? = null
    private var upcomingCollector: Job? = null
    private var playbackCollector: Job? = null
    private var playRequestJob: Job? = null
    private var currentPlayRequestId: String? = null
    private var searchJob: Job? = null
    // the car's last search, so a row it kept can still be played after the query moved on
    private var lastSearch: List<SongItem> = emptyList()
    private var lastPlaybackKey: Pair<String?, Boolean>? = null

    fun install() {
        if (installed) return
        installed = true
        observe(IosCarPlayEvents.libraryRequest, ::requestLibrary)
        observe(IosCarPlayEvents.upcomingRequest) { publishUpcoming(queue?.upcoming?.value.orEmpty()) }
        observe(IosCarPlayEvents.playRequest, ::requestPlay)
        observe(IosCarPlayEvents.searchRequest, ::requestSearch)
        // a reconnecting car missed every change made while it was away, so it gets the current state even if unchanged
        observe(IosCarPlayEvents.playbackRequest) { player?.let { publishPlaybackState(it.state.value, force = true) } }
        attachRuntime()
    }

    private fun attachRuntime() {
        val runtime = IosPlaybackRuntime
        val currentLibrary = runtime.library
        val currentPlayer = runtime.player
        val currentQueue = runtime.queue
        library = currentLibrary
        player = currentPlayer
        queue = currentQueue

        savedCollector?.cancel()
        recentCollector?.cancel()
        upcomingCollector?.cancel()
        playbackCollector?.cancel()
        savedCollector = scope.launch {
            currentLibrary.saved.collect { publishLibrary("saved", it) }
        }
        recentCollector = scope.launch {
            currentLibrary.recent.collect { publishLibrary("recent", it) }
        }
        // Up next is the whole app's queue, so the car sees what the phone and its shuffle do
        upcomingCollector = scope.launch {
            currentQueue.upcoming.collect { publishUpcoming(it) }
        }
        playbackCollector = scope.launch {
            currentPlayer.state.collect { state -> publishPlaybackState(state) }
        }
    }

    private fun observe(name: String, handler: (String?) -> Unit) {
        observers += NSNotificationCenter.defaultCenter.addObserverForName(
            name = name,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification -> handler(notification?.`object` as? String) }
    }

    private fun requestLibrary(payload: String?) {
        val request = parse(payload) ?: return
        val collection = request["collection"]?.jsonPrimitive?.content ?: return
        val flow = libraryFlow(collection) ?: return
        val limit = (request["limit"]?.jsonPrimitive?.intOrNull ?: 100).coerceIn(1, 500)
        requestedLimits[collection] = limit
        scope.launch { publishLibrary(collection, flow.first()) }
    }

    private fun publishLibrary(collection: String, songs: List<SongItem>) {
        val limit = requestedLimits[collection] ?: 100
        post(
            IosCarPlayEvents.librarySnapshot,
            buildJsonObject {
                put("collection", collection)
                put("items", itemsOf(songs.take(limit)))
            }.toString(),
        )
    }

    /** Up next as the car's own tab; capped so a long queue never becomes a huge notification payload. */
    private fun publishUpcoming(songs: List<SongItem>) {
        post(
            IosCarPlayEvents.upcomingSnapshot,
            buildJsonObject {
                put("items", itemsOf(songs.take(UpcomingLimit)))
            }.toString(),
        )
    }

    private fun itemsOf(songs: List<SongItem>) = buildJsonArray {
        songs.forEach { song ->
            add(buildJsonObject {
                put("id", song.id)
                put("title", song.title)
                put("artist", song.artists.joinToString { it.name })
                put("isExplicit", song.explicit)
                put("artworkURL", song.thumbnail.orEmpty())
            })
        }
    }

    private fun requestPlay(payload: String?) {
        val request = parse(payload) ?: return
        val requestId = request["requestId"]?.jsonPrimitive?.content ?: return
        val collection = request["collection"]?.jsonPrimitive?.content ?: return
        val songId = request["id"]?.jsonPrimitive?.content ?: return
        val order = (request["ids"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

        currentPlayRequestId?.let { publishPlayResult(it, ready = false) }
        playRequestJob?.cancel()
        currentPlayRequestId = requestId
        playRequestJob = scope.launch {
            try {
                val list = songsFor(collection)
                if (list == null) {
                    publishPlayResult(requestId, ready = false)
                    return@launch
                }
                val song = list.firstOrNull { it.id == songId }
                if (song == null) {
                    publishPlayResult(requestId, ready = false)
                    return@launch
                }

                val activePlayer = player ?: run {
                    publishPlayResult(requestId, ready = false)
                    return@launch
                }
                val activeQueue = queue
                if (activeQueue != null) {
                    // the car picked a row from a list: the rest of that list becomes Up next, as a tap
                    // on the phone does, so Next walks through the album, playlist or search results
                    if (order.isNotEmpty()) activeQueue.replace(restOfList(list, order, songId))
                    activeQueue.play(song)
                } else {
                    activePlayer.play(song)
                }
                val result = withTimeoutOrNull(45_000L) {
                    activePlayer.state.first { state ->
                        state.song?.id == songId && state.error != null ||
                            (state.song?.id == songId && state.isPlaying && !state.isLoading)
                    }
                }
                val ready = result?.song?.id == songId && result.isPlaying && !result.isLoading && result.error == null
                publishPlayResult(requestId, ready)
                if (ready) library?.recordPlay(song)
            } finally {
                if (currentPlayRequestId == requestId) currentPlayRequestId = null
            }
        }
    }

    /** The songs after [songId] in the order the car showed them. */
    private fun restOfList(list: List<SongItem>, order: List<String>, songId: String): List<SongItem> {
        val byId = list.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        val index = ordered.indexOfFirst { it.id == songId }
        return if (index >= 0) ordered.drop(index + 1) else ordered.filterNot { it.id == songId }
    }

    private fun publishPlayResult(requestId: String, ready: Boolean) {
        post(
            IosCarPlayEvents.playResult,
            buildJsonObject {
                put("requestId", requestId)
                put("ready", ready)
            }.toString(),
        )
    }

    private fun publishPlaybackState(state: PlayerState, force: Boolean = false) {
        val key = state.song?.id to state.isPlaying
        if (!force && lastPlaybackKey == key) return
        lastPlaybackKey = key
        post(
            IosCarPlayEvents.playbackChanged,
            buildJsonObject {
                state.song?.id?.let { put("songId", it) }
                put("isPlaying", state.isPlaying)
            }.toString(),
        )
    }

    private fun libraryFlow(collection: String): Flow<List<SongItem>>? = when (collection) {
        "saved" -> library?.saved
        "recent" -> library?.recent
        "upcoming" -> queue?.upcoming
        else -> null
    }

    /** What a car request plays from, as it is right now; null when the collection isn't one of ours. */
    private suspend fun songsFor(collection: String): List<SongItem>? = when (collection) {
        "saved" -> library?.saved?.first()
        "recent" -> library?.recent?.first()
        "upcoming" -> queue?.upcoming?.value.orEmpty()
        "search" -> lastSearch
        else -> null
    }

    /**
     * The car's search box: one request per pause in typing, answered with the same song search the
     * phone's Search tab runs. A newer query cancels the previous one.
     */
    private fun requestSearch(payload: String?) {
        val request = parse(payload) ?: return
        val query = request["query"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            lastSearch = emptyList()
            post(IosCarPlayEvents.searchSnapshot, buildJsonObject { put("items", buildJsonArray { }) }.toString())
            return
        }
        searchJob = scope.launch {
            // a thumb typing on a car screen sends a query a second; only the settled text searches
            kotlinx.coroutines.delay(350)
            val results = IosPlaybackRuntime.youtube.searchSongs(query).getOrNull().orEmpty()
            lastSearch = results.take(SearchLimit)
            post(
                IosCarPlayEvents.searchSnapshot,
                buildJsonObject {
                    put("query", query)
                    put("items", itemsOf(lastSearch))
                }.toString(),
            )
        }
    }

    private fun parse(payload: String?): JsonObject? = payload?.let {
        runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
    }

    private fun post(name: String, payload: String) {
        NSNotificationCenter.defaultCenter.postNotificationName(name, payload)
    }

    private companion object {
        const val UpcomingLimit = 200
        const val SearchLimit = 50
    }
}

private object IosCarPlayEvents {
    private const val prefix = "com.mochyy.mochymusic.ios.carplay."
    const val libraryRequest = "${prefix}libraryRequest"
    const val librarySnapshot = "${prefix}librarySnapshot"
    const val upcomingRequest = "${prefix}upcomingRequest"
    const val upcomingSnapshot = "${prefix}upcomingSnapshot"
    const val searchRequest = "${prefix}searchRequest"
    const val searchSnapshot = "${prefix}searchSnapshot"
    const val playRequest = "${prefix}playRequest"
    const val playResult = "${prefix}playResult"
    const val playbackChanged = "${prefix}playbackChanged"
    const val playbackRequest = "${prefix}playbackRequest"
}
