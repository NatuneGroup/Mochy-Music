// where each module was added from on the web, and whether a newer copy waits there; looked at now and then so github's limits are never hit

package com.mochyy.mochymusic.modules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** where mochy can look for a newer copy of a module */
sealed interface ModuleSource {
    /** a github project's release zip, with the tag and file stamp it was added at */
    data class GitHub(val owner: String, val repo: String, val tag: String, val stamp: String) : ModuleSource

    /** a service's manifest.json, with a fingerprint of the one that was added */
    data class Link(val base: String, val fingerprint: String) : ModuleSource

    /** what to check again to get the newer copy */
    val link: String
        get() = when (this) {
            is GitHub -> "https://github.com/$owner/$repo"
            is Link -> base
        }

    /** where it comes from, the way the module's page shows it */
    val label: String
        get() = when (this) {
            is GitHub -> "github.com/$owner/$repo"
            is Link -> base.substringAfter("://")
        }
}

/** a newer copy waiting: its tag or version, null when the author changed it without saying which */
data class ModuleUpdate(val version: String?)

class ModuleUpdates(private val store: ModuleStore, private val http: ModuleHttp) {

    fun source(id: String): ModuleSource? {
        val o = store.string(SourceKey + id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
        return when (o.str("kind")) {
            "github" -> ModuleSource.GitHub(o.str("owner"), o.str("repo"), o.str("tag"), o.str("stamp"))
            "link" -> ModuleSource.Link(o.str("base"), o.str("fingerprint"))
            else -> null
        }
    }

    fun update(id: String): ModuleUpdate? {
        val o = store.string(UpdateKey + id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
        return ModuleUpdate(o.str("version").takeIf { it.isNotBlank() })
    }

    fun checkedAt(id: String): Long? = store.long(CheckedKey + id, 0L).takeIf { it > 0L }

    /** what a module was just added or updated from; a zip from the phone has no source and stops being checked */
    fun installed(id: String, source: ModuleSource?) {
        store.putString(SourceKey + id, source?.let { toJson(it).toString() })
        store.remove(UpdateKey + id)
        store.putLong(CheckedKey + id, now())
    }

    fun forget(id: String) {
        store.remove(SourceKey + id)
        store.remove(UpdateKey + id)
        store.remove(CheckedKey + id)
    }

    /** asks the module's source whether it changed; [force] skips the wait between checks. blocking, so off the main thread */
    fun check(id: String, installedVersion: String, force: Boolean, preferDebug: Boolean) {
        val source = source(id) ?: return
        val time = now()
        if (!force && time - (checkedAt(id) ?: 0L) < CheckEveryMs) return
        // written first, so a source that's down or a rate limit isn't asked again on every visit
        store.putLong(CheckedKey + id, time)
        val found = when (source) {
            is ModuleSource.GitHub -> newerRelease(source, preferDebug) ?: return
            is ModuleSource.Link -> newerManifest(source, installedVersion) ?: return
        }
        val update = found.getOrNull()
        if (update == null) store.remove(UpdateKey + id)
        else store.putString(UpdateKey + id, buildJsonObject { put("version", update.version.orEmpty()) }.toString())
    }

    // a result of null means the source couldn't be read, so whatever was known before stays
    private fun newerRelease(source: ModuleSource.GitHub, preferDebug: Boolean): Result<ModuleUpdate?>? {
        val link = GitHubReleases.Link(source.owner, source.repo, tag = null)
        val release = (GitHubReleases.release(http, link, preferDebug) as? GitHubReleases.Found.Newest)?.release ?: return null
        val changed = release.tag != source.tag || release.stamp != source.stamp
        return Result.success(if (changed) ModuleUpdate(release.tag) else null)
    }

    private fun newerManifest(source: ModuleSource.Link, installedVersion: String): Result<ModuleUpdate?>? {
        val response = http.get("${source.base}/manifest.json", JsonHeaders) ?: return null
        if (!response.ok) return null
        val text = response.text
        if (fingerprint(text) == source.fingerprint) return Result.success(null)
        val manifest = manifestOf(text) ?: return null
        val version = manifest.str("version")
        return Result.success(ModuleUpdate(version.takeIf { it.isNotBlank() && it != installedVersion }))
    }

    // a manifest is json with a name or an id, the way the app's own parse reads it; its version is all an update needs
    private fun manifestOf(text: String): JsonObject? {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val named = listOf("name", "id").any { key ->
            val name = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            !name.isNullOrBlank()
        }
        return root.takeIf { named }
    }

    private fun toJson(source: ModuleSource): JsonObject = when (source) {
        is ModuleSource.GitHub -> buildJsonObject {
            put("kind", "github")
            put("owner", source.owner)
            put("repo", source.repo)
            put("tag", source.tag)
            put("stamp", source.stamp)
        }
        is ModuleSource.Link -> buildJsonObject {
            put("kind", "link")
            put("base", source.base)
            put("fingerprint", source.fingerprint)
        }
    }

    // what org.json's optString gave: missing reads as blank, never throws
    private fun JsonObject.str(name: String): String = (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    @OptIn(ExperimentalTime::class)
    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {
        private const val SourceKey = "source_"
        private const val UpdateKey = "update_"
        private const val CheckedKey = "update_checked_"
        // github lets an app without a key ask 60 times an hour, so each module is looked at a few times a day at most
        private const val CheckEveryMs = 6L * 60 * 60 * 1000
        private val JsonHeaders = mapOf("Accept" to "application/json")

        /** a short hash of a manifest's text, so any change to it counts, even one that forgot to bump the version */
        fun fingerprint(text: String): String = text.trim().encodeUtf8().sha1().hex().take(20)
    }
}
