/*
 * Instance protocols adapted from Natune's TIDAL, Qobuz, Deezer and Amazon provider addons.
 * © Natune contributors and morieattonkatsu. GPL-3.0.
 * https://github.com/NatuneGroup/Natune/tree/dev/addons/modules/extension
 */
package com.mochyy.mochymusic.module.natune

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.CatalogTrack
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.FieldKind
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleField
import com.mochyy.mochymusic.module.ModuleFields
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.module.TrackMatcher
import com.mochyy.mochymusic.module.TrackQuery
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

enum class NatuneSource(
    val title: String,
    val options: List<StreamOption>,
) {
    TIDAL("TIDAL", listOf(
        StreamOption("HI_RES_LOSSLESS", "Hi-res", "Highest available lossless quality."),
        StreamOption("LOSSLESS", "Lossless", "CD-quality lossless audio."),
        StreamOption("high", "High", "Smaller AAC stream."),
    )),
    QOBUZ("Qobuz", listOf(
        StreamOption("27", "Hi-res", "Up to 192 kHz."),
        StreamOption("7", "Hi-res 96 kHz", "Up to 96 kHz."),
        StreamOption("6", "Lossless", "CD-quality lossless audio."),
        StreamOption("high", "MP3", "Smaller MP3 stream."),
    )),
    DEEZER("Deezer", listOf(
        StreamOption("FLAC", "Lossless", "FLAC audio."),
        StreamOption("high", "Any supported quality", "FLAC or MP3, depending on the instance."),
    )),
    AMAZON("Amazon Music", listOf(
        StreamOption("UHD", "Ultra HD", "Highest available lossless quality."),
        StreamOption("HD", "HD", "CD-quality lossless audio."),
        StreamOption("high", "Standard", "Smaller stream."),
    )),
}

class NatuneInstanceModule(private val source: NatuneSource) : SourceModule, ModuleFields {
    override val info = ModuleInfo(
        id = "natune-${source.name.lowercase()}",
        name = source.title,
        version = "1.0.0",
        description = "Plays matched recordings through a compatible Natune instance you configure.",
        sourceUrl = "https://github.com/NatuneGroup/Natune",
        author = "Natune contributors",
    )
    override val choices = listOf(ModuleChoice("quality", "Quality", source.options))
    override val fields = listOf(ModuleField(
        id = "instance",
        label = "Instance URL",
        kind = FieldKind.Text,
        hint = "https://instance.example",
        description = "The HTTPS address of your ${source.title} instance.",
    ))

    private lateinit var host: ModuleHost
    @Volatile private var instance: String? = null
    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun onFields(values: Map<String, String>) {
        instance = values["instance"]?.trim()?.let(::httpsUrl)
            ?.takeIf { it.rawPath.orEmpty() in setOf("", "/") && it.rawQuery == null && it.rawFragment == null }
            ?.toASCIIString()?.trimEnd('/')
    }

    override fun accountState(): AccountState = if (instance != null) {
        AccountState.SignedIn("Configured instance")
    } else {
        AccountState.SignedOut
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult {
        val base = instance ?: return ResolveResult.Unavailable("Set a compatible instance URL in Modules.")
        val query = (listOf(track.title) + track.artists.take(1)).joinToString(" ").trim()
        if (query.isEmpty()) return ResolveResult.NotFound("No track title")
        val quality = source.options.firstOrNull { it.id == picks["quality"] } ?: source.options.first()
        return try {
            val candidates = search(base, query).mapNotNull(::catalogTrack)
            for (match in TrackMatcher.ranked(track, candidates).take(3)) {
                val audio = try {
                    resolveAudio(base, match.track, quality)
                } catch (failure: InstanceFailure) {
                    if (failure.code == 404) null else throw failure
                } ?: continue
                return ResolveResult.Found(SourceStream(
                    url = audio.url,
                    format = audio.format,
                    sourceLabel = info.name,
                    qualityLabel = audio.codec ?: "Audio",
                    matched = MatchedTrack(match.track.id, match.track.title, match.track.artist,
                        match.track.album, match.track.durationMs, match.score),
                    codec = audio.codec,
                    sampleRateHz = audio.sampleRate,
                ))
            }
            ResolveResult.NotFound("No matching full track")
        } catch (failure: InstanceFailure) {
            ResolveResult.Unavailable(if (failure.code == 429 || failure.code == 503) {
                "The instance is temporarily unavailable. Try again later."
            } else {
                "The instance returned HTTP ${failure.code}."
            })
        } catch (_: SerializationException) {
            ResolveResult.Unavailable("The instance did not return readable JSON.")
        } catch (_: IOException) {
            ResolveResult.Unavailable("Could not reach the configured service.")
        }
    }

    override suspend fun diagnose(
        track: TrackQuery,
        picks: Map<String, String>,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult {
        val result = resolve(track, picks)
        val detail = when (result) {
            is ResolveResult.Found -> result.stream.matched.title
            is ResolveResult.NotFound -> result.reason
            is ResolveResult.Unavailable -> result.reason
        }
        report(DiagnosticStep(info.name, result is ResolveResult.Found, detail))
        return result
    }

    private fun search(base: String, query: String): List<JsonObject> {
        val response = when (source) {
            NatuneSource.TIDAL -> get(base, "/search/", "s" to query, "q" to query, "limit" to "20")
            NatuneSource.QOBUZ -> get(base, "/api/get-music", "q" to query, "offset" to "0")
            NatuneSource.DEEZER, NatuneSource.AMAZON -> get("https://api.deezer.com", "/search/track", "q" to query, "limit" to "20")
        }
        return trackRows(response)?.take(20)?.mapNotNull { it as? JsonObject }.orEmpty()
    }

    private fun resolveAudio(base: String, track: CatalogTrack, quality: StreamOption): Audio? =
        when (source) {
            NatuneSource.TIDAL -> {
                // Use a direct track or an unencrypted BTS manifest. Some high-res instances give only DASH.
                val requested = if (quality.id == "high") "HIGH" else quality.id
                val tiers = listOf("HI_RES_LOSSLESS", "LOSSLESS", "HIGH").dropWhile { it != requested }
                tiers.firstNotNullOfOrNull { tier ->
                    val root = get(base, "/track/", "id" to track.id, "quality" to tier) as? JsonObject
                    root?.let(::tidalAudio)
                }
            }
            NatuneSource.QOBUZ -> {
                val format = if (quality.id == "high") "5" else quality.id
                val root = get(base, "/api/download-music", "track_id" to track.id, "quality" to format) as? JsonObject
                root?.takeUnless(::protectedOrPreview)?.let { info ->
                    val audio = info.obj("data") ?: info.obj("result") ?: info
                    directAudio(audio)
                }
            }
            NatuneSource.DEEZER -> deezerAudio(base, track, losslessOnly = quality.id == "FLAC")
            NatuneSource.AMAZON -> {
                val format = if (quality.id == "high") "SD_HIGH" else quality.id
                val root = get(base, "/api/track/", "track" to track.title, "artist" to track.artist,
                    "album" to track.album.orEmpty(), "duration" to (track.durationMs?.div(1000)?.toString().orEmpty()),
                    "quality" to format, "intent" to "stream") as? JsonObject
                root?.takeUnless(::protectedOrPreview)?.let { response ->
                    listOfNotNull(response, response.obj("data"), response.obj("track"), response.obj("result"))
                        .firstNotNullOfOrNull { payload ->
                            val title = payload.text("title", "track_name", "name")
                            val artist = artistOf(payload["artist"]) ?: payload.text("artist_name")
                            if (!track.title.equals(title, ignoreCase = true) ||
                                artist?.contains(track.artist, ignoreCase = true) != true) null
                            else directAudio(payload)
                        }
                }
            }
        }

    private fun deezerAudio(base: String, track: CatalogTrack, losslessOnly: Boolean): Audio? {
        val recording = get("https://api.deezer.com", "/track/${track.id}") as? JsonObject
        val isrc = recording?.text("isrc") ?: track.isrc
        val urls = listOfNotNull(
            isrc?.let { "$base/stream/?isrc=${URLEncoder.encode(it, "UTF-8")}" },
            "$base/stream/${track.id}",
        )
        for (url in urls) {
            val response = request(base, url, mapOf("Range" to "bytes=0-15"))
            if (response.code != 206 || response.body.size < 4) continue
            val first = response.body
            val flac = first[0] == 0x66.toByte() && first[1] == 0x4c.toByte() &&
                first[2] == 0x61.toByte() && first[3] == 0x43.toByte()
            val mp3 = first[0] == 0x49.toByte() && first[1] == 0x44.toByte() && first[2] == 0x33.toByte() ||
                first[0] == 0xff.toByte() && (first[1].toInt() and 0xe0) == 0xe0
            if (flac || mp3 && !losslessOnly) {
                return Audio(url, StreamFormat.Progressive, if (flac) "FLAC" else "MP3", null)
            }
        }
        return null
    }

    private fun tidalAudio(root: JsonObject): Audio? {
        if (protectedOrPreview(root)) return null
        val payload = root.obj("data") ?: root
        if (protectedOrPreview(payload)) return null
        directAudio(payload)?.let { return it }
        val encoded = payload.text("manifest")?.takeIf { it.length <= 128_000 } ?: return null
        if (payload.text("manifestMimeType") == "application/dash+xml") return null
        val bytes = try {
            Base64.getDecoder().decode(encoded.replace('-', '+').replace('_', '/'))
        } catch (_: IllegalArgumentException) {
            return null
        }
        val manifest = Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject ?: return null
        val url = (manifest["urls"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.let(::httpsUrl) }
            ?: return null
        return directAudio(manifest, url.toASCIIString(), sampleRate(payload))
    }

    private fun get(base: String, path: String, vararg query: Pair<String, String>): JsonElement {
        val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        val response = request(base, "$base$path?$encoded", mapOf("Accept" to "application/json"))
        if (!response.ok) throw InstanceFailure(response.code)
        return Json.parseToJsonElement(response.text)
    }

    private fun request(base: String, url: String, headers: Map<String, String>): HttpResponse {
        if (System.currentTimeMillis() < (cooldownUntil[base] ?: 0)) throw InstanceFailure(429)
        val response = host.http(HttpRequest(url, headers = headers))
        if (response.code == 429 || response.code == 503) {
            val seconds = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 21_600L) ?: 60L
            cooldownUntil[base] = System.currentTimeMillis() + seconds * 1000L
            throw InstanceFailure(response.code)
        }
        return response
    }
}

private class InstanceFailure(val code: Int) : Exception()
private data class Audio(val url: String, val format: StreamFormat, val codec: String?, val sampleRate: Int?)

private fun trackRows(value: JsonElement?, depth: Int = 0): JsonArray? =
    when {
        value is JsonArray -> value
        depth >= 4 || value !is JsonObject -> null
        else -> listOf("tracks", "items", "results", "data").firstNotNullOfOrNull { trackRows(value[it], depth + 1) }
    }

private fun catalogTrack(item: JsonObject): CatalogTrack? {
    val id = item.text("id", "track_id", "trackId")?.takeIf { it.all(Char::isDigit) } ?: return null
    val title = item.text("title", "name", "track_name") ?: return null
    val version = item.text("version")
    val artists = ((item["artists"] ?: item["contributors"]) as? JsonArray)?.mapNotNull(::artistOf).orEmpty()
    val artist = artists.takeIf { it.isNotEmpty() }?.joinToString(", ")
        ?: artistOf(item["performer"]) ?: artistOf(item["artist"]) ?: return null
    val album = item.obj("album")?.text("title", "name") ?: item.text("album")
    return CatalogTrack(
        id = id,
        title = if (version != null && !title.contains(version, ignoreCase = true)) "$title ($version)" else title,
        artist = artist,
        album = album,
        durationMs = item.text("duration")?.toLongOrNull()?.takeIf { it in 1..86_400 }?.times(1000L),
        isrc = item.text("isrc"),
    )
}

private fun artistOf(value: JsonElement?): String? = when (value) {
    is JsonObject -> value.text("name")
    is JsonPrimitive -> value.contentOrNull?.takeIf { value.isString && it.isNotBlank() }
    else -> null
}

private fun directAudio(info: JsonObject, url: String? = null, rate: Int? = null): Audio? {
    if (protectedOrPreview(info)) return null
    val uri = (url ?: info.text("url", "downloadUrl", "download_url", "stream_url", "streamUrl", "link"))
        ?.let(::httpsUrl) ?: return null
    val mime = info.text("mimeType", "mime_type")?.lowercase().orEmpty()
    val path = uri.path.orEmpty().lowercase()
    val format = when {
        path.endsWith(".m3u8") || "mpegurl" in mime -> StreamFormat.Hls
        path.endsWith(".mpd") || "dash+xml" in mime -> return null
        else -> StreamFormat.Progressive
    }
    val codec = info.text("codecs", "codec")?.uppercase() ?: when {
        path.endsWith(".flac") || "flac" in mime -> "FLAC"
        path.endsWith(".mp3") || "mpeg" in mime && format == StreamFormat.Progressive -> "MP3"
        path.endsWith(".m4a") || mime == "audio/mp4" -> "AAC"
        else -> null
    }
    return Audio(uri.toASCIIString(), format, codec, sampleRate(info) ?: rate)
}

private fun protectedOrPreview(info: JsonObject): Boolean =
    listOf("sample", "is_preview", "preview", "drm", "encrypted").any { (info[it] as? JsonPrimitive)?.booleanOrNull == true } ||
        info.text("encryptionType")?.equals("NONE", ignoreCase = true) == false ||
        info.text("assetPresentation", "type")?.equals("PREVIEW", ignoreCase = true) == true ||
        info.text("decryption_key", "decryptionKey", "encryption") != null

private fun sampleRate(info: JsonObject): Int? =
    (info["sampleRate"] ?: info["sampling_rate"] ?: info["sample_rate"])
        .let { (it as? JsonPrimitive)?.doubleOrNull }
        ?.takeIf { it.isFinite() && it > 0 && it <= 384_000 }
        ?.let { (if (it < 1000) it * 1000 else it).roundToInt() }

private fun httpsUrl(value: String): URI? = try {
    URI(value).takeIf { it.scheme.equals("https", ignoreCase = true) && it.host != null && it.rawUserInfo == null }
} catch (_: java.net.URISyntaxException) {
    null
}

private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull {
    (this[it] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
