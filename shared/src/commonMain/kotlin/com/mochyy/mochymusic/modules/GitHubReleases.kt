// a github project's link turned into the module zip on its newest release, the one with mochyymodule in its file name

package com.mochyy.mochymusic.modules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

object GitHubReleases {

    /** a project, and the release [tag] when the link named one; null tag means the newest */
    data class Link(val owner: String, val repo: String, val tag: String?)

    /** a release's module zip as github lists it; [stamp] changes whenever the file is uploaded again, even under the same tag */
    data class Release(val tag: String, val asset: String, val url: String, val stamp: String)

    sealed interface Found {
        /** the zip's own file name and its bytes, what the add sheet shows it came from, and the release it was; the caller writes it down */
        data class Zip(val name: String, val bytes: ByteArray, val from: String, val release: Release) : Found
        /** the release and its zip, read without downloading anything */
        data class Newest(val release: Release) : Found
        data object NoRelease : Found
        data object NoModule : Found
        data object Unreachable : Found
    }

    /** github.com/owner/repo, with /releases, /releases/latest, /releases/tag/v1 or /tree/… after it; a file link (/blob/, /raw/) stays a plain link */
    fun parse(link: String): Link? {
        val trimmed = link.trim().removeSuffix("/")
        val rest = trimmed.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        if (!rest.startsWith("github.com/", ignoreCase = true)) return null
        val parts = rest.substringAfter('/').substringBefore('?').substringBefore('#').split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        if (!Name.matches(owner) || !Name.matches(repo)) return null
        if (parts.getOrNull(2) in FileViews) return null
        val tag = if (parts.getOrNull(2) == "releases" && parts.getOrNull(3) == "tag") parts.getOrNull(4) else null
        return Link(owner, repo, tag)
    }

    /** the release the link points at and its module zip, without downloading the zip; blocking, so off the main thread */
    fun release(http: ModuleHttp, link: Link, preferDebug: Boolean): Found {
        val api = "https://api.github.com/repos/${link.owner}/${link.repo}/releases/" + (link.tag?.let { "tags/$it" } ?: "latest")
        val response = http.get(api, ReleaseHeaders) ?: return Found.Unreachable
        // latest skips drafts and pre-releases, so a project with only those reads as having none
        if (response.code == 404) return Found.NoRelease
        if (!response.ok) return Found.Unreachable
        val release = runCatching { Json.parseToJsonElement(response.text).jsonObject }.getOrNull() ?: return Found.Unreachable
        val asset = pickAsset(release, preferDebug) ?: return Found.NoModule
        val tag = release.str("tag_name").ifBlank { release.str("name") }.ifBlank { asset.name }
        return Found.Newest(Release(tag, asset.name, asset.url, asset.stamp))
    }

    /** the release's module zip, downloaded; blocking, so off the main thread */
    fun download(http: ModuleHttp, link: Link, preferDebug: Boolean): Found {
        val release = when (val found = release(http, link, preferDebug)) {
            is Found.Newest -> found.release
            else -> return found
        }
        val response = http.get(release.url, ZipHeaders, MaxZipBytes) ?: return Found.Unreachable
        if (!response.ok) return Found.Unreachable
        val name = release.asset.replace(Regex("[^A-Za-z0-9._-]"), "-")
        return Found.Zip(name, response.body, "github.com/${link.owner}/${link.repo} · ${release.tag}", release)
    }

    private class Asset(val name: String, val url: String, val stamp: String)

    // the zips with mochyymodule in their name; with a debug and a release one side by side, the one signed like this mochy
    private fun pickAsset(release: JsonObject, preferDebug: Boolean): Asset? {
        val assets = release["assets"] as? JsonArray ?: return null
        val zips = assets.mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            val name = asset.str("name")
            val url = asset.str("browser_download_url")
            val fits = name.contains(Marker, ignoreCase = true) && name.endsWith(".zip", ignoreCase = true) && url.startsWith("https://")
            // the id and upload time change when an author swaps the file for a fixed one without a new tag
            val stamp = "${asset.num("id")}:${asset.str("updated_at")}:${asset.num("size")}"
            if (fits && asset.num("size") <= MaxZipBytes) Asset(name, url, stamp) else null
        }
        if (zips.size <= 1) return zips.firstOrNull()
        return zips.firstOrNull { it.name.contains("debug", ignoreCase = true) == preferDebug } ?: zips.first()
    }

    // what org.json's optString and optLong gave: missing reads as blank or zero, never throws
    private fun JsonObject.str(name: String): String = (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.num(name: String): Long = (this[name] as? JsonPrimitive)?.longOrNull ?: 0L

    private val ReleaseHeaders = mapOf(
        "Accept" to "application/vnd.github+json",
        "User-Agent" to "Mochy",
    )

    private val ZipHeaders = mapOf("User-Agent" to "Mochy")

    /** what a release file's name has to have in it for mochy to take it */
    const val Marker = "mochyymodule"
    private const val MaxZipBytes = 40L * 1024 * 1024
    private val Name = Regex("[A-Za-z0-9._-]{1,100}")
    private val FileViews = setOf("blob", "raw", "issues", "pulls", "actions", "wiki")
}
