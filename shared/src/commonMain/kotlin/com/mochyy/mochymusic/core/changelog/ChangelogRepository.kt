// ChangelogRepository.kt

package com.mochyy.mochymusic.core.changelog

import com.mochyy.mochymusic.core.platform.platformFilesDirectory
import com.mochyy.mochymusic.core.platform.releaseDateText
import com.mochyy.mochymusic.core.updater.GithubApi
import com.mochyy.mochymusic.core.updater.UpdateOwner
import com.mochyy.mochymusic.core.updater.UpdateRepo
import com.mochyy.mochymusic.core.updater.UpdateRepoUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path.Companion.toPath

data class ChangelogSection(val title: String, val items: List<String>)

data class ReleaseMetadata(val tagName: String, val name: String, val date: String, val imageUrl: String?)

data class CachedChangelogData(val sections: List<ChangelogSection>, val image: String?, val description: String?, val warning: String?)

// What one release's changelog.json says, before the screen decides what to show and what to keep.
data class ChangelogPayload(
    val sections: List<ChangelogSection>,
    val image: String?,
    val description: String?,
    val warning: String?,
)

// What the screen got: the payload, and whether it came from the cache (the little "cached" badge).
data class ChangelogRead(val payload: ChangelogPayload, val cached: Boolean)

// The changelog screen's network and cache half, the same urls, headers and cache file names the
// android app has always used. The cache file sits in the app's private files folder.
object ChangelogRepository {
    private const val UserAgent = "Mochy-Changelog-App"
    private const val Prefix = "changelog_cache_"
    private const val Suffix = ".json"

    private val fs = FileSystem.SYSTEM

    private fun folder(platformContext: Any) = platformFilesDirectory(platformContext).toPath()

    private fun cacheFile(platformContext: Any, tag: String) = folder(platformContext) / "$Prefix$tag$Suffix"

    // only the version being shown keeps its cache; the others go on the next open
    fun cleanup(platformContext: Any, currentVersionTag: String) {
        val dir = folder(platformContext)
        runCatching {
            fs.listOrNull(dir).orEmpty()
                .filter { it.name.startsWith(Prefix) && it.name.endsWith(Suffix) }
                .filter { it.name != "$Prefix$currentVersionTag$Suffix" }
                .forEach { fs.delete(it, mustExist = false) }
        }
    }

    fun loadCached(platformContext: Any, tag: String): CachedChangelogData? = runCatching {
        val file = cacheFile(platformContext, tag)
        if (!fs.exists(file)) return null
        val json = Json.parseToJsonElement(fs.read(file) { readUtf8() }) as? JsonObject ?: return null
        val sectionsArray = json["sections"] as? JsonArray ?: return null
        val sections = sectionsArray.mapNotNull { element ->
            val section = element as? JsonObject ?: return@mapNotNull null
            val items = (section["items"] as? JsonArray)?.mapNotNull { item ->
                (item as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
            }.orEmpty()
            ChangelogSection(section.text("title").orEmpty(), items)
        }
        CachedChangelogData(
            sections = sections,
            image = json.text("image")?.takeIf { it.isNotBlank() },
            description = json.text("description")?.takeIf { it.isNotBlank() },
            warning = json.text("warning")?.takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    private fun saveCached(platformContext: Any, tag: String, payload: ChangelogPayload) {
        runCatching {
            val json = buildJsonObject {
                put("sections", buildJsonArray {
                    payload.sections.forEach { section ->
                        add(
                            buildJsonObject {
                                put("title", section.title)
                                put("items", buildJsonArray { section.items.forEach { add(JsonPrimitive(it)) } })
                            },
                        )
                    }
                })
                put("image", payload.image ?: "")
                put("description", payload.description ?: "")
                put("warning", payload.warning ?: "")
            }
            val dir = folder(platformContext)
            fs.createDirectories(dir)
            fs.write(cacheFile(platformContext, tag)) { writeUtf8(json.toString()) }
        }
    }

    // one release's changelog.json; null when github answered with something other than 200
    suspend fun fetch(tag: String): ChangelogPayload? {
        val answer = GithubApi.get("$UpdateRepoUrl/releases/download/$tag/changelog.json", "application/json", UserAgent)
            ?: return null
        if (answer.code != 200) return null
        val json = runCatching { Json.parseToJsonElement(answer.text) as? JsonObject }.getOrNull() ?: return null
        val sections = mutableListOf<ChangelogSection>()
        (json["changelog"] as? JsonArray)?.forEach { element ->
            val section = element as? JsonObject
            if (section != null) {
                val title = section.text("title").orEmpty()
                val items = (section["items"] as? JsonArray)?.mapNotNull { item ->
                    (item as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                }.orEmpty()
                if (title.isNotBlank() || items.isNotEmpty()) {
                    sections.add(ChangelogSection(title, items))
                }
            } else {
                val item = (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()
                if (item.isNotBlank()) {
                    if (sections.isEmpty() || sections[0].title.isNotBlank()) {
                        sections.add(0, ChangelogSection("", emptyList()))
                    }
                    sections[0] = sections[0].copy(items = sections[0].items + item)
                }
            }
        }
        return ChangelogPayload(
            sections = sections,
            image = json.text("image"),
            description = json.text("description"),
            warning = json.text("warning"),
        )
    }

    // the release the screen is showing, fetched and cached for offline opens
    suspend fun load(platformContext: Any, tag: String): Result<ChangelogRead> {
        val cached = loadCached(platformContext, tag)
        if (cached != null) {
            return Result.success(
                ChangelogRead(
                    ChangelogPayload(cached.sections, cached.image, cached.description, cached.warning),
                    cached = true,
                ),
            )
        }
        val payload = runCatching { fetch(tag) }.getOrNull()
            ?: return Result.failure(IllegalStateException("HTTP Error for $tag"))
        saveCached(platformContext, tag, payload)
        return Result.success(ChangelogRead(payload, cached = false))
    }

    // every release that carries a changelog.json, newest first, as github lists them
    suspend fun releases(): Result<List<ReleaseMetadata>> {
        val answer = GithubApi.get(
            "https://api.github.com/repos/$UpdateOwner/$UpdateRepo/releases",
            "application/vnd.github+json",
            UserAgent,
        ) ?: return Result.failure(IllegalStateException("couldn't reach GitHub"))
        if (answer.code != 200) return Result.failure(IllegalStateException("GitHub API Error ${answer.code}"))
        val array = runCatching { Json.parseToJsonElement(answer.text) as? JsonArray }.getOrNull()
            ?: return Result.failure(IllegalStateException("GitHub API returned no releases"))
        val list = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val tagName = obj.text("tag_name") ?: return@mapNotNull null
            if (!tagName.startsWith("v", ignoreCase = true)) return@mapNotNull null
            val name = obj.text("name") ?: tagName
            val publishedAt = obj.text("published_at") ?: ""
            val formattedDate = releaseDateText(publishedAt) ?: publishedAt
            val assets = obj["assets"] as? JsonArray
            val changelogUrl = assets?.firstNotNullOfOrNull { asset ->
                val json = asset as? JsonObject ?: return@firstNotNullOfOrNull null
                if (json.text("name") == "changelog.json") json.text("browser_download_url") else null
            }
            if (changelogUrl == null) null else ReleaseMetadata(tagName, name, formattedDate, null)
        }
        return Result.success(list)
    }
}

internal fun JsonObject.text(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.content
}
