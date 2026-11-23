// DiscordGateway.kt
//
// One live connection to Discord's gateway, signed in with the OAuth token, the way Discord's
// own Social SDK does it. A status sent over it lasts only as long as the connection: when
// Mochy is closed or killed, Discord drops it by itself rather than leaving an old song up.
//
// It doesn't reconnect on its own. When it closes it calls [onClosed] once, and the owner opens
// a new one when there's something to show again. The socket comes from Ktor's WebSockets plugin
// on the same client the web calls use, so every platform can keep the connection.

package com.mochyy.mochymusic.discord

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.random.Random
import com.mochyy.mochymusic.utils.ioDispatcher
import okio.IOException

class DiscordGateway(
    private val token: String,
    private val onClosed: (code: Int) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val json = Json { ignoreUnknownKeys = true }
    private val ready = CompletableDeferred<Unit>()

    @Volatile private var session: DefaultClientWebSocketSession? = null
    @Volatile private var sequence: Long? = null
    @Volatile private var heartbeatAcked = true
    @Volatile private var closed = false
    private var reader: Job? = null
    private var heartbeat: Job? = null

    val isReady: Boolean get() = ready.isCompleted && !closed

    /** Opens the connection and waits for Discord to say it's ready. False if it never does. */
    suspend fun connect(): Boolean {
        val opened = try {
            DiscordHttp.client.webSocketSession { url(GatewayUrl) }
        } catch (e: IOException) {
            return false
        } catch (e: Exception) {
            return false
        }
        session = opened
        reader = scope.launch { read(opened) }
        // a connection that fails on the way ends the wait at once, rather than at the timeout
        val connected = try {
            withTimeoutOrNull(ReadyTimeoutMs) { ready.await() } != null
        } catch (e: IOException) {
            false
        }
        if (!connected) close(code = 4009)
        return connected
    }

    /** Shows [activity] on the profile, or nothing at all when it's null. */
    suspend fun sendPresence(activity: JsonObject?): Boolean {
        if (!isReady) return false
        return send(
            op = OpPresenceUpdate,
            data = buildJsonObject {
                put("since", JsonNull)
                put("activities", buildJsonArray { if (activity != null) add(activity) })
                put("status", "online")
                put("afk", false)
            },
        )
    }

    fun close(code: Int = 1000) {
        if (closed) return
        closed = true
        ready.completeExceptionally(IOException("Discord gateway closed with $code"))
        heartbeat?.cancel()
        reader?.cancel()
        session?.coroutineContext?.cancel()
        session = null
        scope.cancel()
        onClosed(code)
    }

    private suspend fun send(op: Int, data: JsonElement): Boolean {
        val frame = buildJsonObject {
            put("op", op)
            put("d", data)
        }
        val current = session ?: return false
        return try {
            current.send(Frame.Text(frame.toString()))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Reads frames until the socket ends, then reports the close code Discord gave it. */
    private suspend fun read(opened: DefaultClientWebSocketSession) {
        try {
            for (frame in opened.incoming) {
                if (frame is Frame.Text) onMessage(frame.readText())
            }
        } catch (e: Exception) {
            // a dropped connection is a closed one
        }
        val reason = runCatching { opened.closeReason.await() }.getOrNull()
        close(code = reason?.code?.toInt() ?: 4000)
    }

    private fun onMessage(text: String) {
        val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        message["s"]?.jsonPrimitive?.let { if (it !is JsonNull) sequence = it.long }
        when (message["op"]?.jsonPrimitive?.intOrNull) {
            OpHello -> {
                val interval = message["d"]?.jsonObject?.get("heartbeat_interval")?.jsonPrimitive?.long ?: 41_250L
                startHeartbeat(interval)
                identify()
            }
            OpHeartbeatAck -> heartbeatAcked = true
            OpHeartbeat -> sendHeartbeat()
            // Discord wants a fresh connection; the owner makes one
            OpReconnect, OpInvalidSession -> close(code = 4000)
            OpDispatch -> if (message["t"]?.jsonPrimitive?.contentOrNull == "READY") ready.complete(Unit)
        }
    }

    private fun identify() {
        scope.launch {
            send(
                op = OpIdentify,
                data = buildJsonObject {
                    put("token", "Bearer $token")
                    put("capabilities", Capabilities)
                    put("intents", Intents)
                    put("properties", buildJsonObject {
                        put("os", "Android")
                        put("browser", "Mochy")
                        put("device", "Android")
                    })
                },
            )
        }
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            // Discord asks for a random first beat, so a crowd of reconnecting clients spreads out
            delay((intervalMs * Random.nextDouble()).toLong())
            while (isActive) {
                if (!heartbeatAcked) {
                    // no answer to the last beat: the connection is dead even if the socket isn't
                    close(code = 4009)
                    return@launch
                }
                sendHeartbeat()
                delay(intervalMs)
            }
        }
    }

    private fun sendHeartbeat() {
        heartbeatAcked = false
        scope.launch {
            send(OpHeartbeat, sequence?.let { JsonPrimitive(it) } ?: JsonNull)
        }
    }

    companion object {
        private const val GatewayUrl = "wss://gateway.discord.gg/?v=9&encoding=json"
        private const val ReadyTimeoutMs = 20_000L

        /** Discord closes with this when the token is no good any more */
        const val CloseAuthFailed = 4004

        private const val OpDispatch = 0
        private const val OpHeartbeat = 1
        private const val OpIdentify = 2
        private const val OpPresenceUpdate = 3
        private const val OpReconnect = 7
        private const val OpInvalidSession = 9
        private const val OpHello = 10
        private const val OpHeartbeatAck = 11

        // What the Social SDK sends. Friends' presence (1 shl 23) is left out: Mochy never reads
        // it, and with many friends online it would keep the radio busy for nothing.
        private const val Capabilities = (1 shl 4) or (1 shl 5) or (1 shl 12) or (1 shl 16)
        private const val Intents = (1 shl 12) or (1 shl 18) or (1 shl 19) or (1 shl 22) or
            (1 shl 27) or (1 shl 28) or (1 shl 29)
    }
}
