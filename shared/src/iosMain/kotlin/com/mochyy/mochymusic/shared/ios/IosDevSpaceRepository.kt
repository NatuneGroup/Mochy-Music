// IosDevSpaceRepository.kt
// The dev spaces page's GitHub numbers on iOS. :app reads them with HttpURLConnection and org.json;
// iOS reads the same endpoints through Ktor's Darwin engine and kotlinx.serialization, and keeps the
// last good numbers when GitHub refuses, exactly as the Android one does.

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.mochyy.mochymusic.shared.ios

import com.mochyy.mochymusic.core.updater.GithubOwner
import com.mochyy.mochymusic.core.updater.GithubRepo
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevIssue
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevSpaceRepository
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevSpaceStats
import com.mochyy.mochymusic.utils.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSFileCreationDate
import platform.Foundation.NSFileManager

/**
 * Repo numbers for the dev spaces page, straight from GitHub. The page and its playground card share
 * this one object through Koin, as they share `AppDevSpaceRepository` on Android, so opening one
 * never fetches twice.
 *
 * [sinceMillis] is when this copy of the app was built: the executable's creation date, which the
 * page turns into "what happened since this build". Android reads BuildConfig for the same value.
 */
internal class IosDevSpaceRepository : DevSpaceRepository {

    private val http = HttpClient(Darwin) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000L
            requestTimeoutMillis = 12_000L
            socketTimeoutMillis = 12_000L
        }
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val stats = MutableStateFlow<DevSpaceStats?>(null)
    private val loadingFlag = MutableStateFlow(false)
    private val errorFlag = MutableStateFlow<String?>(null)

    private val sinceMillis: Long by lazy { executableBuiltAt() }

    override val current: StateFlow<DevSpaceStats?> = stats.asStateFlow()
    override val loading: StateFlow<Boolean> = loadingFlag.asStateFlow()
    override val error: StateFlow<String?> = errorFlag.asStateFlow()

    override suspend fun refresh(force: Boolean, maxAgeMs: Long) {
        val known = stats.value
        if (!force && known != null && now() - known.fetchedAt < maxAgeMs) return
        // two openers at once (the page and its card): a forced one waits its turn, a lazy one leaves it
        if (force) mutex.lock() else if (!mutex.tryLock()) return
        loadingFlag.value = true
        try {
            val since = isoInstant(sinceMillis)
            val old = stats.value ?: DevSpaceStats()
            var failure: String? = null

            fun <T> guarded(block: () -> T): T? = try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RateLimited) {
                failure = "GitHub's rate limit is reached, try again in a few minutes"
                null
            } catch (_: Throwable) {
                failure = failure ?: "Couldn't reach GitHub"
                null
            }

            val fresh = withContext(ioDispatcher) {
                coroutineScope {
                    val open = async { guarded { searchCount("is:open") } }
                    val closed = async { guarded { searchCount("is:closed") } }
                    val newIssues = async { guarded { searchCount("created:>=$since") } }
                    val newResolved = async { guarded { searchCount("is:closed closed:>=$since") } }
                    val commits = async { guarded { commitsSince(since) } }
                    val releases = async { guarded { releasesSince(sinceMillis) } }
                    val issues = async { guarded { latestIssues() } }
                    DevSpaceStats(
                        openTotal = open.await() ?: old.openTotal,
                        closedTotal = closed.await() ?: old.closedTotal,
                        newIssues = newIssues.await() ?: old.newIssues,
                        newResolved = newResolved.await() ?: old.newResolved,
                        commits = commits.await() ?: old.commits,
                        releases = releases.await() ?: old.releases,
                        issues = issues.await() ?: old.issues,
                        fetchedAt = now(),
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

    private suspend fun get(path: String): Pair<String, String?> {
        val response = http.get("$Api$path") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "Mochy")
        }
        val code = response.status.value
        // github answers 403 and 429 when a client is over its limit, the same pair :app checks
        if (code == 403 || code == 429) throw RateLimited()
        check(response.status.isSuccess()) { "GitHub answered $code" }
        return response.bodyAsText() to response.headers[HttpHeaders.Link]
    }

    /** issues (never pull requests) matching the qualifiers, counted by github */
    private suspend fun searchCount(qualifiers: String): Int {
        val query = "repo:$Repo is:issue $qualifiers".encodeURLParameter()
        val body = get("/search/issues?q=$query&per_page=1").first
        return json.parseToJsonElement(body).jsonObject["total_count"]?.jsonPrimitive?.int ?: 0
    }

    /** one commit per page, so the last page's number is the count */
    private suspend fun commitsSince(since: String): Int {
        val (body, link) = get("/repos/$Repo/commits?since=$since&per_page=1")
        val last = link?.let { Regex("[?&]page=(\\d+)>; rel=\"last\"").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (last != null) return last
        return (json.parseToJsonElement(body) as? JsonArray)?.size ?: 0
    }

    private suspend fun releasesSince(sinceMillis: Long): Int {
        val body = get("/repos/$Repo/releases?per_page=100").first
        val array = json.parseToJsonElement(body) as? JsonArray ?: return 0
        var count = 0
        for (element in array) {
            val published = (element as? JsonObject)
                ?.get("published_at")?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: continue
            val at = runCatching { kotlin.time.Instant.parse(published).toEpochMilliseconds() }.getOrNull() ?: continue
            if (at >= sinceMillis) count++
        }
        return count
    }

    private suspend fun latestIssues(): List<DevIssue> {
        val body = get("/repos/$Repo/issues?state=all&per_page=60&sort=created&direction=desc").first
        val array = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        val issues = ArrayList<DevIssue>()
        for (element in array) {
            val issue = element as? JsonObject ?: continue
            // the issues endpoint lists pull requests beside issues, with a pull_request object
            if (issue.containsKey("pull_request")) continue
            val number = issue["number"]?.jsonPrimitive?.int ?: continue
            val title = issue["title"]?.jsonPrimitive?.content ?: continue
            val labels = (issue["labels"] as? JsonArray)
                ?.mapNotNull { label -> (label as? JsonObject)?.get("name")?.jsonPrimitive?.content }
                .orEmpty()
            val created = issue["created_at"]?.jsonPrimitive?.content
            val createdAt = created?.let {
                runCatching { kotlin.time.Instant.parse(it).toEpochMilliseconds() }.getOrNull()
            } ?: 0L
            issues += DevIssue(
                number = number,
                title = title,
                open = issue["state"]?.jsonPrimitive?.content == "open",
                labels = labels,
                url = issue["html_url"]?.jsonPrimitive?.content.orEmpty(),
                createdAtMillis = createdAt,
            )
        }
        return issues
    }

    private fun now(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun isoInstant(millis: Long): String = kotlin.time.Instant.fromEpochMilliseconds(millis).toString()

    /** When this build of the app was written to the phone; a missing date falls back to a month ago. */
    private fun executableBuiltAt(): Long {
        val path = NSBundle.mainBundle.executablePath ?: return now() - 30L * 24 * 60 * 60 * 1000
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
        val created = attributes?.get(NSFileCreationDate) as? NSDate
        val millis = created?.timeIntervalSince1970?.times(1000.0)?.toLong() ?: 0L
        return millis.takeIf { it > 0L } ?: (now() - 30L * 24 * 60 * 60 * 1000)
    }

    private companion object {
        const val Api = "https://api.github.com"
        val Repo = "$GithubOwner/$GithubRepo"
    }
}
