// LinkHostJson.kt

package com.mochyy.mochymusic.link.phone

import org.json.JSONObject

// The link's wire bodies are org.json on the app's side of the seam, while the host carries the
// answer's plain text. This keeps every existing caller's JSONObject body and answer working.
internal suspend fun PhoneLinkHost.tell(
    device: LiveDevice,
    path: String,
    body: JSONObject?,
): JSONObject? = tell(device, path, body?.toString())
    ?.let { runCatching { JSONObject(it) }.getOrNull() }
