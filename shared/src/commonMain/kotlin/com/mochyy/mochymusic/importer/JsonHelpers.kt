// JsonHelpers.kt

package com.mochyy.mochymusic.importer

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

// lenient readers for third-party json: a missing or differently-typed field is null, never a crash

fun JsonElement.path(vararg keys: String): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) current = (current as? JsonObject)?.get(key) ?: return null
    return current
}

fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

/** a number or a numeric string, as ids sometimes come either way */
fun JsonObject.idString(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonElement?.array(): List<JsonElement> = (this as? JsonArray).orEmpty()

// a compact numeric offset ("+0000", as tidal sends) has no colon, which the ISO reader needs
private val OFFSET_WITHOUT_COLON = Regex("([+-]\\d{2})(\\d{2})$")

/** a timestamp as services send them (iso with a z, or tidal's +0000 offset), in epoch ms */
@OptIn(kotlin.time.ExperimentalTime::class)
fun dateMs(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    return runCatching { kotlin.time.Instant.parse(text).toEpochMilliseconds() }.getOrNull()
        ?: runCatching {
            kotlin.time.Instant.parse(OFFSET_WITHOUT_COLON.replace(text) { "${it.groupValues[1]}:${it.groupValues[2]}" })
                .toEpochMilliseconds()
        }.getOrNull()
}
