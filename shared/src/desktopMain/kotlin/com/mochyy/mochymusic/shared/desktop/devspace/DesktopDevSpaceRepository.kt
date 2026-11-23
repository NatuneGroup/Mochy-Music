// DesktopDevSpaceRepository.kt
// Dev spaces' repo numbers straight from GitHub, for the desktop build. Android's version lives in
// :app because it reads BuildConfig's build time; here the build time is the jar's, so the page still
// counts "since this build". If GitHub refuses (rate limit, offline) the last good numbers stay and
// the page says why, exactly as on Android.

package com.mochyy.mochymusic.shared.desktop.devspace

import com.mochyy.mochymusic.core.updater.GithubOwner
import com.mochyy.mochymusic.core.updater.GithubRepo
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevIssue
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevSpaceRepository
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevSpaceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter

internal class DesktopDevSpaceRepository : DevSpaceRepository {
    private val stats = MutableStateFlow<DevSpaceStats?>(null)
    private val loadingFlag = MutableStateFlow(false)
    private val errorFlag = MutableStateFlow<String?>(null)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override val current: StateFlow<DevSpaceStats?> = stats.asStateFlow()
    override val loading: StateFlow<Boolean> = loadingFlag.asStateFlow()
    override val error: StateFlow<String?> = errorFlag.asStateFlow()

    override suspend fun refresh(force: Boolean, maxAgeMs: Long) {
        val known = stats.value
        if (!force && known != null && System.currentTimeMillis() - known.fetchedAt < maxAgeMs) return
        // two openers at once (the page and its card): a forced one waits its turn, a lazy one leaves it
        if (force) mutex.lock() else if (!mutex.tryLock()) return
        loadingFlag.value = true
        try {
            val since = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(buildTimeMillis()))
            val old = stats.value ?: DevSpaceStats()
            var failure: String? = null
            fun <T> guarded(block: () -> T): T? = try {
                block()
            } catch (_: RateLimited) {
                failure = "GitHub's rate limit is reached, try again in a few minutes"
                null
            } catch (_: Exception) {
                failure = failure ?: "Couldn't reach GitHub"
                null
            }

            val fresh = withContext(Dispatchers.IO) {
                coroutineScope {
                    val open = async { guarded { searchCount("is:open") } }
                    val closed = async { guarded { searchCount("is:closed") } }
                    val newIssues = async { guarded { searchCount("created:>=$since") } }
                    val newResolved = async { guarded { searchCount("is:closed closed:>=$since") } }
                    val commits = async { guarded { commitsSince(since) } }
                    val releases = async { guarded { releasesSince(buildTimeMillis()) } }
                    val issues = async { guarded { latestIssues() } }
                    DevSpaceStats(
                        openTotal = open.await() ?: old.openTotal,
                        closedTotal = closed.await() ?: old.closedTotal,
                        newIssues = newIssues.await() ?: old.newIssues,
                        newResolved = newResolved.await() ?: old.newResolved,
                        commits = commits.await() ?: old.commits,
                        releases = releases.await() ?: old.releases,
                        issues = issues.await() ?: old.issues,
                        fetchedAt = System.currentTimeMillis(),
                    )
                }
            }
            stats.value = fresh
            errorFlag.value = failure
        } finally {
            loadingFlag.value = false
            mutex.unlock()
        }
    }

    private class RateLimited : Exception()

    private fun get(path: String): Pair<String, String?> {
        val connection = URL("$Api$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Mochy")
        try {
            val code = connection.responseCode
            if (code == 403 || code == 429) throw RateLimited()
            if (code !in 200..299) error("GitHub answered $code")
            return connection.inputStream.bufferedReader().use { it.readText() } to connection.getHeaderField("Link")
        } finally {
            connection.disconnect()
        }
    }

    /** issues (never pull requests) matching the qualifiers, counted by GitHub */
    private fun searchCount(qualifiers: String): Int {
        val query = URLEncoder.encode("repo:$Repo is:issue $qualifiers", "UTF-8")
        val body = get("/search/issues?q=$query&per_page=1").first
        return json.parseToJsonElement(body).jsonObject["total_count"]?.jsonPrimitive?.intOrNull ?: 0
    }

    /** one commit per page, so the last page's number is the count */
    private fun commitsSince(since: String): Int {
        val (body, link) = get("/repos/$Repo/commits?since=$since&per_page=1")
        val last = link?.let { Regex("[?&]page=(\\d+)>; rel=\"last\"").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return last ?: json.parseToJsonElement(body).jsonArray.size
    }

    private fun releasesSince(sinceMillis: Long): Int {
        val released = json.parseToJsonElement(get("/repos/$Repo/releases?per_page=100").first).jsonArray
        var count = 0
        for (element in released) {
            val at = element.jsonObject["published_at"]?.jsonPrimitive?.contentOrNull ?: continue
            if (runCatching { Instant.parse(at).toEpochMilli() }.getOrDefault(Long.MAX_VALUE) >= sinceMillis) count++
        }
        return count
    }

    private fun latestIssues(): List<DevIssue> {
        val issues = json.parseToJsonElement(get("/repos/$Repo/issues?state=all&per_page=60&sort=created&direction=desc").first)
        return buildList {
            for (element in issues.jsonArray) {
                val issue = element.jsonObject
                // the issues endpoint returns pull requests too; the page counts issues only
                if (issue.containsKey("pull_request")) continue
                add(
                    DevIssue(
                        number = issue["number"]?.jsonPrimitive?.intOrNull ?: continue,
                        title = issue["title"]?.jsonPrimitive?.contentOrNull ?: continue,
                        open = issue["state"]?.jsonPrimitive?.contentOrNull == "open",
                        labels = labelsOf(issue["labels"]),
                        url = issue["html_url"]?.jsonPrimitive?.contentOrNull ?: continue,
                        createdAtMillis = issue["created_at"]?.jsonPrimitive?.contentOrNull
                            ?.let { at -> runCatching { Instant.parse(at).toEpochMilli() }.getOrNull() }
                            ?: continue,
                    ),
                )
            }
        }
    }

    private fun labelsOf(element: JsonElement?): List<String> {
        val array = (element as? JsonArray) ?: return emptyList()
        return array.mapNotNull { label -> label.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
    }

    private companion object {
        const val Api = "https://api.github.com"
        const val Repo = "$GithubOwner/$GithubRepo"

        /**
         * When this desktop build's classes were written. A jar carries the build's timestamp, so
         * "since this build" means the same thing it means on Android's BuildConfig.
         */
        fun buildTimeMillis(): Long = runCatching {
            val source = DesktopDevSpaceRepository::class.java.protectionDomain.codeSource.location
            java.io.File(source.toURI()).lastModified()
        }.getOrDefault(0L)
    }
}
