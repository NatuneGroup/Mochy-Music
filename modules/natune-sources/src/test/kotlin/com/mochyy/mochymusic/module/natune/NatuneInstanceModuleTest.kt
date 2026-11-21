package com.mochyy.mochymusic.module.natune

import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.TrackQuery
import java.io.File
import java.net.URI
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NatuneInstanceModuleTest {
    private val track = TrackQuery("Test Song", listOf("Test Artist"), durationMs = 180_000)
    private val catalog = """{"data":{"tracks":{"items":[{"id":7,"title":"Test Song","artist":{"name":"Test Artist"},"duration":180}]}}}"""

    @Test
    fun tidalResolvesAnUnencryptedManifest() = runBlocking {
        val manifest = Base64.getEncoder().encodeToString(
            """{"urls":["https://audio.example/song.flac"],"encryptionType":"NONE","codecs":"flac"}""".encodeToByteArray(),
        )
        val module = module(NatuneSource.TIDAL) { request ->
            when (URI(request.url).path) {
                "/search/" -> reply(catalog)
                "/track/" -> reply("""{"data":{"manifestMimeType":"application/vnd.tidal.bts","manifest":"$manifest","sampleRate":96}}""")
                else -> error("Unexpected request")
            }
        }
        val result = module.resolve(track, emptyMap()) as ResolveResult.Found
        assertEquals("https://audio.example/song.flac", result.stream.url)
        assertEquals("FLAC", result.stream.codec)
        assertEquals(96_000, result.stream.sampleRateHz)
    }

    @Test
    fun qobuzUsesTheRequestedFormatAndRejectsPreviews() = runBlocking {
        val module = module(NatuneSource.QOBUZ) { request ->
            when (URI(request.url).path) {
                "/api/get-music" -> reply(catalog)
                "/api/download-music" -> {
                    assertTrue(request.url.contains("track_id=7&quality=6"))
                    reply("""{"data":{"url":"https://audio.example/sample.flac","is_preview":true}}""")
                }
                else -> error("Unexpected request")
            }
        }
        assertTrue(module.resolve(track, mapOf("quality" to "6")) is ResolveResult.NotFound)
    }

    @Test
    fun deezerAcceptsOnlyAConfirmedPartialAudioResponse() = runBlocking {
        var streamStatus = 200
        val module = module(NatuneSource.DEEZER) { request ->
            when (URI(request.url).path) {
                "/search/track" -> reply(catalog)
                "/track/7" -> reply("""{"isrc":"TEST00000001"}""")
                "/stream/", "/stream/7" -> {
                    assertEquals("bytes=0-15", request.headers["Range"])
                    HttpResponse(streamStatus, "fLaC".encodeToByteArray())
                }
                else -> error("Unexpected request")
            }
        }
        assertTrue(module.resolve(track, emptyMap()) is ResolveResult.NotFound)
        streamStatus = 206
        val result = module.resolve(track, emptyMap()) as ResolveResult.Found
        assertEquals("FLAC", result.stream.codec)
        assertTrue(result.stream.headers.isEmpty())
    }

    @Test
    fun amazonRejectsAnInstanceReturningADifferentRecording() = runBlocking {
        val module = module(NatuneSource.AMAZON) { request ->
            when (URI(request.url).path) {
                "/search/track" -> reply(catalog)
                "/api/track/" -> reply("""{"track":{"title":"Other Song","artist":"Other Artist","stream_url":"https://audio.example/song.flac"}}""")
                else -> error("Unexpected request")
            }
        }
        assertTrue(module.resolve(track, emptyMap()) is ResolveResult.NotFound)
    }

    @Test
    fun rateLimitIsHonoredOnLaterAttempts() = runBlocking {
        var calls = 0
        val module = module(NatuneSource.TIDAL) {
            calls++
            HttpResponse(429, ByteArray(0), mapOf("retry-after" to "60"))
        }
        repeat(2) { assertTrue(module.resolve(track, emptyMap()) is ResolveResult.Unavailable) }
        assertEquals(1, calls)
    }

    private fun reply(json: String): HttpResponse = HttpResponse(200, json.encodeToByteArray())

    private fun module(source: NatuneSource, respond: (HttpRequest) -> HttpResponse): NatuneInstanceModule =
        NatuneInstanceModule(source).apply {
            attach(object : ModuleHost {
                override val folder = File(".")
                override val storage = object : ModuleStorage {
                    override fun get(key: String): String? = null
                    override fun set(key: String, value: String?) = Unit
                }
                override fun http(request: HttpRequest): HttpResponse = respond(request)
                override fun log(message: String) = Unit
            })
            onFields(mapOf("instance" to "https://instance.example"))
        }
}
