// DesktopPaths.kt
// The one place that says where Mochy keeps its files on a desktop: settings, databases, the image
// cache, the song cache, downloads and the single-instance folder. Each follows the platform's usual
// home for it, the same split Android makes between filesDir and cacheDir.

package com.mochyy.mochymusic.shared.desktop

import java.io.File

object DesktopPaths {
    /** `~/.config/Mochy` on Linux, `Application Support/Mochy` on macOS, `%APPDATA%/Mochy` on Windows. */
    val appData: File by lazy {
        val home = File(System.getProperty("user.home"))
        when (osName()) {
            Os.Mac -> home.resolve("Library/Application Support/Mochy")
            Os.Windows -> (envDir("APPDATA") ?: home.resolve("AppData/Roaming")).resolve("Mochy")
            Os.Other -> xdgDir("XDG_CONFIG_HOME") ?: home.resolve(".config/Mochy")
        }
    }

    /** the platform's cache home: `~/.cache` on Linux, `Library/Caches` on macOS, `%LOCALAPPDATA%` on Windows */
    val cacheHome: File by lazy {
        val home = File(System.getProperty("user.home"))
        when (osName()) {
            Os.Mac -> home.resolve("Library/Caches")
            Os.Windows -> envDir("LOCALAPPDATA") ?: home.resolve("AppData/Local")
            Os.Other -> xdgDir("XDG_CACHE_HOME") ?: home.resolve(".cache")
        }
    }

    /** covers and artist pictures, the desktop twin of Android's image cache (coil keeps its files under it) */
    val images: File get() = cacheHome.resolve("Mochy/image_cache")

    /** streamed-audio cache, the desktop twin of the ExoPlayer cache */
    val songCache: File get() = cacheHome.resolve("Mochy/audio_cache")

    val downloads: File get() = appData.resolve("downloads")

    /** the lock and the "show your window" requests of the single-instance check */
    val instance: File get() = appData.resolve("instance")

    /** creates the folders that must exist before the window opens; a read-only home simply has none */
    fun ensure() {
        listOf(appData, images, songCache, downloads, instance).forEach { runCatching { it.mkdirs() } }
    }

    private enum class Os { Mac, Windows, Other }

    private fun osName(): Os {
        val name = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            name.startsWith("mac") -> Os.Mac
            name.startsWith("windows") -> Os.Windows
            else -> Os.Other
        }
    }

    private fun envDir(variable: String): File? =
        System.getenv(variable)?.takeUnless(String::isBlank)?.let(::File)

    private fun xdgDir(variable: String): File? =
        envDir(variable)?.takeIf(File::isAbsolute)?.resolve("Mochy")
}
