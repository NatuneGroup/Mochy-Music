// LinkJson.kt

package com.mochyy.mochymusic.link

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

// The link's wire bodies in the app's shape: optString/optLong/optBoolean/optInt, which is what
// org.json used to give every route. Missing or null values stay null/0/default, never throw.
internal fun JsonObject.string(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.content
}

internal fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

internal fun JsonObject.boolean(key: String, default: Boolean): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

internal fun JsonObject.int(key: String, default: Int): Int =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: default
