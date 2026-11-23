// UpdateDownloads.kt

package com.mochyy.mochymusic.core.updater

import com.mochyy.mochymusic.core.platform.platformDownloadsDirectory
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Clock

// The APKs older updates left behind. The folder name, the ".apk" filter and the one-day clean-up
// are the app's own, now shared so any platform's update screen can show and clear them.
object UpdateDownloads {
    private const val FolderName = "mochy_updates"
    private const val ApkSuffix = ".apk"
    private const val OldAfterMs = 24L * 60L * 60L * 1000L

    private val fs = FileSystem.SYSTEM

    // the folder as a plain path string, so :app doesn't need okio to name it
    fun directoryPath(platformContext: Any): String =
        (platformDownloadsDirectory(platformContext).toPath() / FolderName).toString()

    private fun folder(platformContext: Any): Path = platformDownloadsDirectory(platformContext).toPath() / FolderName

    private fun apks(platformContext: Any): List<Path> {
        val dir = folder(platformContext)
        return runCatching {
            fs.listOrNull(dir).orEmpty().filter { path ->
                fs.metadataOrNull(path)?.isRegularFile == true && path.name.endsWith(ApkSuffix, ignoreCase = true)
            }
        }.getOrDefault(emptyList())
    }

    fun count(platformContext: Any): Int = apks(platformContext).size

    fun clear(platformContext: Any): Boolean {
        var allDeleted = true
        apks(platformContext).forEach { path ->
            if (!runCatching { fs.delete(path, mustExist = false) }.isSuccess) allDeleted = false
        }
        return allDeleted
    }

    fun clearOld(platformContext: Any) {
        val oneDayAgo = Clock.System.now().toEpochMilliseconds() - OldAfterMs
        apks(platformContext).forEach { path ->
            val modified = runCatching { fs.metadataOrNull(path)?.lastModifiedAtMillis }.getOrNull() ?: return@forEach
            if (modified < oneDayAgo) runCatching { fs.delete(path, mustExist = false) }
        }
    }
}
