// CommitsRepository.kt

package com.mochyy.mochymusic.core.commitscreen

import com.mochyy.mochymusic.core.platform.commitDateText
import com.mochyy.mochymusic.core.updater.GithubApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class CommitData(
    val sha: String,
    val message: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val authorLogin: String?,
    val date: String,
    val htmlUrl: String
)

// The commit screen's network half: the same repository and page the android app has always read,
// with the first line of each commit message as its title.
object CommitsRepository {
    private const val UserAgent = "Mochy-Changelog-App"
    private const val Url = "https://api.github.com/repos/realidkroo/Mochy-Music/commits?branch=main&per_page=50"

    suspend fun load(): Result<List<CommitData>> {
        val answer = GithubApi.get(Url, "application/vnd.github+json", UserAgent)
            ?: return Result.failure(IllegalStateException("couldn't reach GitHub"))
        if (answer.code != 200) return Result.failure(IllegalStateException("GitHub API Error ${answer.code}"))
        val array = runCatching { Json.parseToJsonElement(answer.text) as? JsonArray }.getOrNull()
            ?: return Result.failure(IllegalStateException("GitHub API returned no commits"))
        val list = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val sha = obj.text("sha") ?: return@mapNotNull null
            val htmlUrl = obj.text("html_url") ?: return@mapNotNull null
            val commit = obj["commit"] as? JsonObject ?: return@mapNotNull null
            val fullMessage = commit.text("message").orEmpty()
            val message = fullMessage.lines().firstOrNull { it.isNotBlank() } ?: fullMessage
            val author = commit["author"] as? JsonObject
            val authorName = author?.text("name") ?: "Unknown"
            val rawDate = author?.text("date").orEmpty()
            val login = (obj["author"] as? JsonObject)?.text("login")
            val avatar = (obj["author"] as? JsonObject)?.text("avatar_url")
            CommitData(
                sha = sha,
                message = message,
                authorName = authorName,
                authorAvatarUrl = avatar,
                authorLogin = login,
                date = commitDateText(rawDate) ?: rawDate,
                htmlUrl = htmlUrl,
            )
        }
        return Result.success(list)
    }
}

internal fun JsonObject.text(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.content
}
