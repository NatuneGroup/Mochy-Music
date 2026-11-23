// IosPaths.kt
// The sandbox folders every iOS app owns, and the folder-size reads the shared settings pages ask for.
// Kept in one place so the database, the settings file, the storage page and the image cache all name
// the same folders.

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mochyy.mochymusic.shared.ios

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSFileSystemSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** The three folders iOS hands an app, resolved once per process. */
internal object IosPaths {
    /** where Mochy's database, its settings file and the library live. */
    val applicationSupport: String by lazy { directory(NSApplicationSupportDirectory) }

    /** what iOS may delete when space runs low: covers, the URL cache, Mochy's own caches. */
    val caches: String by lazy { directory(NSCachesDirectory) }

    /** what the user sees in the Files app when the app shares its documents. */
    val documents: String by lazy { directory(NSDocumentDirectory) }

    private fun directory(searchPath: ULong): String {
        val url: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = searchPath,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        return checkNotNull(url?.path) { "iOS did not return a $searchPath folder" }
    }
}

/** Everything under [path], files only; a folder that isn't there costs nothing. */
internal fun sizeOfDirectory(path: String): Long {
    val manager = NSFileManager.defaultManager
    val entries = manager.subpathsAtPath(path) ?: return 0L
    var total = 0L
    for (entry in entries) {
        val name = entry as? String ?: continue
        total += sizeOfFile("$path/$name")
    }
    return total
}

/** One file's size; a directory, a link target or a missing entry counts as nothing. */
internal fun sizeOfFile(path: String): Long {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return 0L
    if ((attributes[NSFileType] as? String) == NSFileTypeDirectory) return 0L
    return (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
}

/** The drive's total and free bytes, as the Files app reports them. */
internal fun volumeSize(path: String): Pair<Long, Long> {
    val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(path, error = null)
        ?: return 0L to 0L
    val total = (attributes[NSFileSystemSize] as? NSNumber)?.longLongValue ?: 0L
    val free = (attributes[NSFileSystemFreeSize] as? NSNumber)?.longLongValue ?: 0L
    return total to free
}

/** Empties [path] and keeps the folder itself, so the next measure reads zero instead of a missing folder. */
internal fun clearDirectory(path: String) {
    val manager = NSFileManager.defaultManager
    val entries = manager.contentsOfDirectoryAtPath(path, error = null) ?: return
    for (entry in entries) {
        val name = entry as? String ?: continue
        manager.removeItemAtPath("$path/$name", error = null)
    }
}

/** True when [path] is one of the database files a SQLite connection keeps open beside its database. */
internal fun isDatabaseFile(name: String, database: String): Boolean =
    name == database || name.startsWith("$database-")
