// PlatformInfo.desktop.kt

package com.mochyy.mochymusic.core.platform

import java.io.File

// the desktop launcher's own version (set by the packaging step) and its per-user data folder
actual fun platformVersionName(platformContext: Any): String =
    System.getProperty("mochy.version")?.takeIf { it.isNotBlank() } ?: "0.0.0"

actual fun platformFilesDirectory(platformContext: Any): String {
    val home = System.getProperty("user.home") ?: "."
    return File(home, ".mochy/files").apply { mkdirs() }.absolutePath
}

actual fun platformDownloadsDirectory(platformContext: Any): String {
    val home = System.getProperty("user.home") ?: "."
    return File(home, ".mochy/downloads").apply { mkdirs() }.absolutePath
}
