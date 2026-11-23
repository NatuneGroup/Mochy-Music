package com.mochyy.mochymusic.core.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mochyy.mochymusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// GithubOwner, GithubRepo, InstagramUrl, ThreadsUrl and DiscordUrl live in :shared
// (core/updater/ProjectLinks.kt), with the dev space pages that read them.
// The release catalog itself (release parsing, the cache, the channels) lives in :shared
// (core/updater/UpdateCatalog.kt); this file is the Android build's binding to it plus the APK
// download and install, which only Android does.

const val GithubProfileUrl = "https://github.com/$GithubOwner"
const val GithubRepoUrl = "https://github.com/$GithubOwner/$GithubRepo"

// What the shared catalog needs to know about this build; every value is what the version sheet and
// the launch check have always compared.
private val installedBuild: UpdateBuild by lazy {
    UpdateBuild(
        versionName = BuildConfig.VERSION_NAME,
        architecture = runCatching { BuildConfig.ARCHITECTURE }.getOrDefault("universal"),
        variant = installedVariant(BuildConfig.FLAVOR),
        isNightly = installedIsNightly,
    )
}

internal val catalog: UpdateCatalog by lazy { UpdateCatalog(installedBuild) }

/** The newest release on [channel], whatever is installed. */
suspend fun getLatestRelease(channel: UpdateChannel, force: Boolean = false): Result<GithubRelease?> =
    catalog.latest(channel, force)

/** What "Downgrade to" offers, null when there's none. */
suspend fun getPreviousRelease(channel: UpdateChannel, force: Boolean = false): Result<GithubRelease?> =
    catalog.previous(channel, force)

fun isNewerThanInstalled(candidateVersionText: String): Boolean = catalog.isNewerThanInstalled(candidateVersionText)

suspend fun downloadApk(
    context: Context,
    release: GithubRelease,
    onProgress: (Float) -> Unit,
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val url = release.apkUrl ?: error("This release has no APK attached")
        val target = apkFileFor(context, release)

        if (isDownloaded(context, release)) {
            onProgress(1f)
            return@runCatching target
        }
        if (target.exists()) target.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "mochy-android")
        }
        val total = release.apkSizeBytes.takeIf { it > 0 }
            ?: connection.contentLengthLong.takeIf { it > 0 }
            ?: -1L

        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read
                    if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
        target
    }
}

fun apkFileFor(context: Context, release: GithubRelease): File =
    File(context.cacheDir, release.apkName ?: "mochy-update.apk")

fun isDownloaded(context: Context, release: GithubRelease): Boolean {
    val f = apkFileFor(context, release)
    if (!f.exists() || f.length() <= 0L) return false
    return release.apkSizeBytes <= 0L || f.length() == release.apkSizeBytes
}

fun canInstallPackages(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

fun requestInstallPermission(context: Context) {
    // many tv boxes have no page for one app, so it falls back to the whole list, then security, then settings
    val pages = listOf(
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + context.packageName)),
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
        Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS),
        Intent(android.provider.Settings.ACTION_SETTINGS),
    )
    pages.firstOrNull { page ->
        runCatching { context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

fun installApk(context: Context, apk: File): Boolean {
    if (!canInstallPackages(context)) {
        requestInstallPermission(context)
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "unknown size"
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000f)
    else -> String.format("%.0f KB", bytes / 1000f)
}
