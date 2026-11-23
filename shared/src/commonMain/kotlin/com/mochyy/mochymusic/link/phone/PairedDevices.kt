// PairedDevices.kt

package com.mochyy.mochymusic.link.phone

import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.link.DeviceKind
import com.mochyy.mochymusic.link.LinkKeySealer
import com.mochyy.mochymusic.link.LinkKeyValueStore
import com.mochyy.mochymusic.link.boolean
import com.mochyy.mochymusic.link.int
import com.mochyy.mochymusic.link.long
import com.mochyy.mochymusic.link.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// a device this phone is paired with; the key is what both sides show on every call
@Immutable
data class PairedDevice(
    val id: String,
    val name: String,
    val key: String,
    val kind: DeviceKind,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val lastSyncAt: Long = 0L,
    // where a phone or tablet's own link server was last found; a tv says where it is on every hello instead
    val host: String? = null,
    val port: Int = 0,
    // this phone may play, skip and change settings there, and for another phone the other way round too
    val remoteControl: Boolean = true,
    // steering starts by itself as soon as the device turns up
    val autoControl: Boolean = false,
    // a tv may stream this phone's downloads and song files
    val streamLocal: Boolean = true,
    // a tv's library is replaced by this phone's and kept in step
    val mirrorLibrary: Boolean = true,
)

// a paired device that's around right now, with where it listens for this phone
@Immutable
data class LiveDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val host: String,
    val port: Int,
    val key: String,
    val seenAt: Long,
)

// paired devices as json in the link's own store (the keys sealed), so the boot receiver can read them without hilt
internal object PairedDeviceStore {
    // the names are from when only tvs could pair, kept so nobody has to pair again
    const val Prefs = "mochy_link_tvs"
    private const val ListKey = "tvs"

    fun read(store: LinkKeyValueStore, sealer: LinkKeySealer): List<PairedDevice> {
        val text = store.getString(ListKey) ?: return emptyList()
        val array = runCatching { Json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val json = element as? JsonObject ?: return@mapNotNull null
            PairedDevice(
                id = json.string("id").orEmpty(),
                name = json.string("name").orEmpty(),
                // sealed by this phone's vault; one from another phone's backup can't be opened, so that device pairs again
                key = sealer.open(json.string("key").orEmpty())?.ifBlank { null } ?: return@mapNotNull null,
                // saved before kinds existed, so it can only be a tv
                kind = DeviceKind.fromWire(json.string("kind")?.ifBlank { null }, DeviceKind.Tv),
                pairedAt = json.long("pairedAt"),
                lastSeenAt = json.long("lastSeenAt"),
                lastSyncAt = json.long("lastSyncAt"),
                host = json.string("host")?.ifBlank { null },
                port = json.long("port").toInt(),
                remoteControl = json.boolean("remoteControl", true),
                autoControl = json.boolean("autoControl", false),
                streamLocal = json.boolean("streamLocal", true),
                mirrorLibrary = json.boolean("mirrorLibrary", true),
            )
        }
    }

    fun write(store: LinkKeyValueStore, list: List<PairedDevice>, sealer: LinkKeySealer) {
        val array = buildJsonArray {
            list.forEach {
                add(
                    buildJsonObject {
                        put("id", it.id)
                        put("name", it.name)
                        put("key", sealer.seal(it.key))
                        put("kind", it.kind.wire)
                        put("pairedAt", it.pairedAt)
                        put("lastSeenAt", it.lastSeenAt)
                        put("lastSyncAt", it.lastSyncAt)
                        put("host", it.host.orEmpty())
                        put("port", it.port)
                        put("remoteControl", it.remoteControl)
                        put("autoControl", it.autoControl)
                        put("streamLocal", it.streamLocal)
                        put("mirrorLibrary", it.mirrorLibrary)
                    },
                )
            }
        }
        store.putString(ListKey, array.toString())
    }

    fun hasAny(store: LinkKeyValueStore): Boolean {
        val saved = store.getString(ListKey)
        return !saved.isNullOrBlank() && saved != "[]"
    }
}

