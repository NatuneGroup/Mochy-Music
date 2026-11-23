// LinkHostJson.kt

package com.mochyy.mochymusic.link.phone

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

// The link's wire bodies are org.json on the app's side of the seam, while the host speaks
// kotlinx.serialization in :shared. This keeps every existing caller's JSONObject body working.
internal suspend fun PhoneLinkHost.tell(
    device: LiveDevice,
    path: String,
    body: JSONObject?,
): JSONObject? = tell(device, path, body?.let { Json.parseToJsonElement(it.toString()) as? JsonObject })
    ?.let { JSONObject(it.toString()) }
