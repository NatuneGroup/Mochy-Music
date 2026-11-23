// PhonePairing.kt

package com.mochyy.mochymusic.link.phone

import com.mochyy.mochymusic.link.DeviceKind
import com.mochyy.mochymusic.link.LinkIdentity
import com.mochyy.mochymusic.link.LinkPairingDoor
import com.mochyy.mochymusic.link.LinkResponse
import com.mochyy.mochymusic.link.PairingCode
import com.mochyy.mochymusic.link.string
import com.mochyy.mochymusic.link.string
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// while the add device sheet is open, this phone shows a code too, so another phone or tablet can pair with it
class PhonePairing(
    private val platformContext: Any,
    private val host: PhoneLinkHost,
) {
    private val door = LinkPairingDoor(platformContext, "PhonePair", ::accept)

    val code: StateFlow<PairingCode?> = door.code

    private val _paired = MutableSharedFlow<PairedDevice>(extraBufferCapacity = 1)

    // someone typed this phone's code and is now paired with it
    val paired: SharedFlow<PairedDevice> = _paired.asSharedFlow()

    fun open() = door.open()

    fun close() = door.close()

    private fun accept(bodyText: String, remoteHost: String, code: String): LinkResponse {
        val body = runCatching { Json.parseToJsonElement(bodyText) as? JsonObject }.getOrNull()
            ?: return LinkResponse.status(400)
        val accepted = host.acceptPairing(body, remoteHost, code).getOrElse {
            return when ((it as? PairException)?.reason) {
                PairException.Reason.Full -> LinkResponse.status(409, PhoneLinkHost.FullReply)
                PairException.Reason.Outdated -> LinkResponse.status(426, "update mochy on the other device")
                else -> LinkResponse.status(400)
            }
        }
        _paired.tryEmit(accepted.device)
        return LinkResponse.json(
            buildJsonObject {
                put("tvId", LinkIdentity.id(platformContext))
                put("tvName", LinkIdentity.name(platformContext))
                put("kind", DeviceKind.of(platformContext).wire)
                put("port", host.port)
                put("pub", accepted.publicKey)
            }.toString(),
        )
    }
}
