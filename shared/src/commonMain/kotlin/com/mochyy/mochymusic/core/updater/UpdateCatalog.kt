// UpdateCatalog.kt

package com.mochyy.mochymusic.core.updater

import com.mochyy.mochymusic.link.LinkKeyValueStore
import com.mochyy.mochymusic.link.PlatformLock
import com.mochyy.mochymusic.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

// a release is x.y.z, a nightly adds a fourth number counting up from the release it was built on (8.3.2.1, 8.3.2.2, then 8.3.3)
data class FullVersion(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val buildNumber: Int = 0,
    val nightly: Int = 0,
) : Comparable<FullVersion> {
    override fun compareTo(other: FullVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (nightly != other.nightly) return nightly.compareTo(other.nightly)
        return buildNumber.compareTo(other.buildNumber)
    }

    // what people read: 8.3.2, 8.3.2.4 for a nightly
    val name: String get() = if (nightly > 0) "$major.$minor.$patch.$nightly" else "$major.$minor.$patch"
}

private val VersionRegex = Regex("""(\d+)\.(\d+)(?:\.(\d+))?(?:\.(\d+))?""")
private val BuildNumberRegex = Regex("""(?:build#|\(#|#|\()(\d+)\)?""", RegexOption.IGNORE_CASE)

fun parseFullVersion(text: String): FullVersion {
    val version = VersionRegex.find(text)?.groupValues
    val buildNumber = BuildNumberRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return FullVersion(
        major = version?.get(1)?.toIntOrNull() ?: 0,
        minor = version?.get(2)?.toIntOrNull() ?: 0,
        patch = version?.getOrNull(3)?.toIntOrNull() ?: 0,
        buildNumber = buildNumber,
        nightly = version?.getOrNull(4)?.toIntOrNull() ?: 0,
    )
}

// release only sees normal github releases, nightly sees the prereleases too and takes whichever is newest
enum class UpdateChannel { Release, Nightly }

data class GithubRelease(
    val title: String,
    val tag: String,
    val version: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
    // marked as a prerelease on github, which is what makes it a nightly
    val isNightly: Boolean = false,
)

sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GithubRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** One file attached to a release. */
data class ApkAsset(val name: String, val url: String, val sizeBytes: Long)

private val Variants = listOf("fdroid", "foss", "gms")

/** foss, gms or fdroid out of the build's flavour name (arm64Foss -> foss), null when it has none. */
fun installedVariant(flavor: String): String? = Variants.firstOrNull { flavor.contains(it, ignoreCase = true) }

/**
 * The APK to install from a release: the one built for this ABI and flavour (mochy-8.4.4-arm64-foss.apk)
 * first. With none, what the updater always picked: the last one named for this ABI, else a universal
 * one, else the first APK.
 */
fun pickApkAsset(assets: List<ApkAsset>, arch: String, variant: String?): ApkAsset? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    val specificArch = arch.isNotBlank() && arch != "universal"
    if (specificArch && variant != null) {
        apks.lastOrNull { it.name.contains(arch, ignoreCase = true) && it.name.contains(variant, ignoreCase = true) }
            ?.let { return it }
    }
    return (if (specificArch) apks.lastOrNull { it.name.contains(arch, ignoreCase = true) } else null)
        ?: apks.lastOrNull { it.name.contains("universal", ignoreCase = true) }
        ?: apks.firstOrNull()
}

// what the running build is, so the catalog can pick its own APK and compare versions without BuildConfig
data class UpdateBuild(
    val versionName: String,
    val architecture: String,
    val variant: String?,
    val isNightly: Boolean,
)

// older releases in this repo are the app under its old id, so "downgrade" to one would add a second app instead
private val FirstReleaseWithThisId = FullVersion(major = 8, minor = 2, patch = 3)

private const val CacheTtlMillis = 15L * 60L * 1000L
private const val ReleasesApi = "https://api.github.com/repos/$UpdateOwner/$UpdateRepo/releases?per_page=20"

/** A release with the version it was parsed to, so it can be compared without parsing again. */
private class ParsedRelease(val version: FullVersion, val release: GithubRelease)

/**
 * The update check, shared by every platform: the same repository, the same release parsing, the same
 * fifteen-minute cache and the same channel rules the Android app has always used. The platform only
 * says which build it is ([UpdateBuild]) and gives the settings store.
 */
class UpdateCatalog(private val build: UpdateBuild) {
    private val cacheLock = PlatformLock()
    private var cachedAt = 0L
    private var cachedResult: Result<List<ParsedRelease>>? = null
    private var inFlight: Deferred<Result<List<ParsedRelease>>>? = null

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Every published release, asked for once and shared for [CacheTtlMillis] unless [force]d. */
    private suspend fun getReleases(force: Boolean): Result<List<ParsedRelease>> {
        val cachedNow = cacheLock.withLock {
            val cached = cachedResult
            if (!force && cached != null && Clock.System.now().toEpochMilliseconds() - cachedAt < CacheTtlMillis) cached else null
        }
        if (cachedNow != null) return cachedNow

        val deferred = cacheLock.withLock {
            inFlight ?: scope.async { fetchReleases() }.also { inFlight = it }
        }

        val result = deferred.await()
        cacheLock.withLock {
            if (inFlight === deferred) inFlight = null
            if (result.isSuccess) {
                cachedResult = result
                cachedAt = Clock.System.now().toEpochMilliseconds()
            }
        }
        return result
    }

    // release only sees releases; nightly sees the prereleases and the releases, and takes the newest of all
    private fun ParsedRelease.inChannel(channel: UpdateChannel) = channel == UpdateChannel.Nightly || !release.isNightly

    /** The newest release on [channel], whatever is installed. */
    suspend fun latest(channel: UpdateChannel, force: Boolean = false): Result<GithubRelease?> =
        getReleases(force).map { list -> list.filter { it.inChannel(channel) }.maxByOrNull { it.version }?.release }

    /** True when the installed build is a nightly, which is what makes the channel defaults and downgrade rules. */
    val installedIsNightly: Boolean get() = build.isNightly

    /**
     * What "Downgrade to" offers, null when there's none: on release, the newest release before the
     * installed x.y.z (an older build of the same version doesn't count); on nightly, the nightly before
     * this one. A nightly never steps down to a release, that can break the library.
     */
    suspend fun previous(channel: UpdateChannel, force: Boolean = false): Result<GithubRelease?> {
        val installed = parseFullVersion(build.versionName).copy(buildNumber = 0)
        return getReleases(force).map { list ->
            when {
                channel == UpdateChannel.Release && build.isNightly -> null
                channel == UpdateChannel.Release -> list.filter { !it.release.isNightly && it.version.copy(buildNumber = 0) < installed }
                !build.isNightly -> null
                else -> list.filter { it.release.isNightly && it.version.copy(buildNumber = 0) < installed }
            }?.maxByOrNull { it.version }?.release
        }
    }

    fun isNewerThanInstalled(candidateVersionText: String): Boolean {
        val candidate = parseFullVersion(candidateVersionText)
        val installed = parseFullVersion(build.versionName)
        return candidate > installed
    }

    // leaving nightly would mean installing an older release over it, which can break the library, so it waits for a newer release
    fun canLeaveNightly(latestRelease: GithubRelease?): Boolean =
        !build.isNightly || (latestRelease != null && isNewerThanInstalled(latestRelease.version))

    private suspend fun fetchReleases(): Result<List<ParsedRelease>> {
        return runCatching {
            val answer = GithubApi.get(ReleasesApi, "application/vnd.github+json", "mochy-android")
                ?: error("Couldn't reach GitHub")
            if (answer.code != 200) error("GitHub answered ${answer.code}")
            val releases = Json.parseToJsonElement(answer.text) as? JsonArray ?: error("GitHub sent no releases")
            val appArch = build.architecture.ifBlank { "universal" }
            val appVariant = build.variant

            releases.mapNotNull { element ->
                val release = element as? JsonObject ?: return@mapNotNull null
                if ((release["draft"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null

                val tag = release.text("tag_name").orEmpty()
                val title = release.text("name").orEmpty().ifBlank { tag }
                val fullVer = parseFullVersion("$title $tag")

                if (fullVer.major == 0 && fullVer.minor == 0 && fullVer.patch == 0) return@mapNotNull null
                if (fullVer.copy(buildNumber = 0) < FirstReleaseWithThisId) return@mapNotNull null

                val displayVersion = if (fullVer.buildNumber > 0) "${fullVer.name} (${fullVer.buildNumber})" else fullVer.name

                val assets = (release["assets"] as? JsonArray)?.mapNotNull { asset ->
                    val json = asset as? JsonObject ?: return@mapNotNull null
                    ApkAsset(
                        name = json.text("name").orEmpty(),
                        url = json.text("browser_download_url").orEmpty(),
                        sizeBytes = (json["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
                    )
                }.orEmpty()
                val picked = pickApkAsset(assets, appArch, appVariant)

                ParsedRelease(
                    version = fullVer,
                    release = GithubRelease(
                        title = title,
                        tag = tag,
                        version = displayVersion,
                        body = release.text("body").orEmpty(),
                        htmlUrl = release.text("html_url").orEmpty().ifBlank { "$UpdateRepoUrl/releases" },
                        apkUrl = picked?.url,
                        apkName = picked?.name,
                        apkSizeBytes = picked?.sizeBytes ?: 0L,
                        publishedAt = release.text("published_at").orEmpty(),
                        isNightly = (release["prerelease"] as? JsonPrimitive)?.booleanOrNull == true,
                    ),
                )
            }
        }
    }
}

// The update switches and the last-seen values, in the app's "settings" preferences file exactly as before.
// The platform hands in that file, so the same keys and defaults are read on every target.
class UpdateSettings(private val store: LinkKeyValueStore) {
    companion object {
        const val Prefs = "settings"
        private const val KeyAutoUpdateCheck = "auto_update_check"
        private const val KeyLastCheckedTime = "last_checked_time"
        private const val KeyBetaUpdates = "beta_updates"
        private const val KeyUpdateAvailable = "update_available"
        private const val KeyUpdateNotifications = "update_notifications"
        private const val KeyUpdateChannel = "update_channel"
    }

    fun updateAvailable(): Boolean = store.getBoolean(KeyUpdateAvailable, false)

    fun saveUpdateAvailable(available: Boolean) = store.putBoolean(KeyUpdateAvailable, available)

    fun autoUpdateCheck(): Boolean = store.getBoolean(KeyAutoUpdateCheck, true)

    fun saveAutoUpdateCheck(enabled: Boolean) = store.putBoolean(KeyAutoUpdateCheck, enabled)

    fun updateNotifications(): Boolean = store.getBoolean(KeyUpdateNotifications, true)

    fun saveUpdateNotifications(enabled: Boolean) = store.putBoolean(KeyUpdateNotifications, enabled)

    fun saveLastCheckedTime(timestamp: String) = store.putString(KeyLastCheckedTime, timestamp)

    fun lastCheckedTime(): String = store.getString(KeyLastCheckedTime).orEmpty()

    fun betaUpdates(): Boolean = store.getBoolean(KeyBetaUpdates, false)

    fun saveBetaUpdates(enabled: Boolean) = store.putBoolean(KeyBetaUpdates, enabled)

    // a nightly build starts out on the nightly channel, everything else on release, until someone switches
    fun channel(installedIsNightly: Boolean): UpdateChannel {
        val saved = store.getString(KeyUpdateChannel)
        return UpdateChannel.entries.firstOrNull { it.name == saved }
            ?: if (installedIsNightly) UpdateChannel.Nightly else UpdateChannel.Release
    }

    fun saveChannel(channel: UpdateChannel) = store.putString(KeyUpdateChannel, channel.name)
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content
