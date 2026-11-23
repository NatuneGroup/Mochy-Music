// LinkPairingDoor.kt

package com.mochyy.mochymusic.link

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile

// while a pairing screen is open, the device waits on the wi-fi for someone who types the code it shows, and the caller answers the one who got it right
class LinkPairingDoor(
    private val platformContext: Any,
    private val serverName: String,
    // given the code that matched too, since the key both sides work out is mixed from it
    private val onPaired: (body: String, remoteHost: String, code: String) -> LinkResponse,
) {
    private val server = LinkHttpServer(serverName, ::route)
    private val lock = PlatformLock()
    private var advert: LinkAdvert? = null

    @Volatile private var wrongTries = 0

    private val _code = MutableStateFlow<PairingCode?>(null)
    val code: StateFlow<PairingCode?> = _code.asStateFlow()

    fun open() = lock.withLock {
        if (server.isRunning) return@withLock
        val name = LinkIdentity.name(platformContext)
        val port = server.start(LinkWire.PairPort)
        newCode()
        advertise(name, port)
    }

    // the advert made before android 17 let us on the wi-fi never went out, so it's made again with the same code
    fun advertiseAgain() = lock.withLock {
        if (!server.isRunning) return@withLock
        advert?.close()
        advertise(LinkIdentity.name(platformContext), server.port)
    }

    private fun advertise(name: String, port: Int) {
        advert = LinkAdvert(
            platformContext,
            LinkWire.PairServiceType,
            "Mochy $name",
            port,
            mapOf(
                LinkWire.AttrId to LinkIdentity.id(platformContext),
                LinkWire.AttrName to name,
                LinkWire.AttrKind to DeviceKind.of(platformContext).wire,
            ),
        )
    }

    fun close() = lock.withLock {
        advert?.close()
        advert = null
        server.stop()
        _code.value = null
    }

    private fun newCode() {
        wrongTries = 0
        _code.value = PairingCode(
            code = ((randomInt() and Int.MAX_VALUE) % 900_000 + 100_000).toString(),
            deviceName = LinkIdentity.name(platformContext),
            address = localAddress(),
        )
    }

    private fun randomInt(): Int = DeviceCrypto.secureRandomBytes(4).fold(0) { value, byte ->
        (value shl 8) or (byte.toInt() and 0xff)
    }

    // this device's address on the wi-fi, for a phone on a network that hides devices from each other
    private fun localAddress(): String? = getLocalIpV4Addresses().firstOrNull()

    private fun route(request: LinkRequest): LinkResponse = when {
        request.path == "/pair/hello" -> LinkResponse.json(
            buildJsonObject {
                put("id", LinkIdentity.id(platformContext))
                put("name", LinkIdentity.name(platformContext))
                put("kind", DeviceKind.of(platformContext).wire)
                put("protocol", LinkWire.ProtocolVersion)
            }.toString(),
        )
        request.path == "/pair" && request.method == "POST" -> pair(request)
        else -> LinkResponse.status(404)
    }

    private fun pair(request: LinkRequest): LinkResponse {
        val body = runCatching { Json.parseToJsonElement(request.bodyText()) as? JsonObject }.getOrNull()
            ?: return LinkResponse.status(400)
        val expected = _code.value?.code ?: return LinkResponse.status(409, "not pairing")
        if (!sameCode(body.string("code").orEmpty(), expected)) {
            // guessing is cut short: a few wrong codes and the screen shows a new one
            wrongTries++
            if (wrongTries >= MaxWrongTries) newCode()
            return LinkResponse.status(403, "wrong code")
        }
        val reply = onPaired(request.bodyText(), request.remoteHost, expected)
        // a used code is never good twice
        if (reply.status == 200) newCode()
        return reply
    }

    private fun sameCode(given: String, expected: String): Boolean = constantTimeEquals(expected, given.trim())

    private companion object {
        const val MaxWrongTries = 5
    }
}
