package com.mochyy.mochymusic.shared.desktop

import com.mochyy.mochymusic.shared.settings.PlaybackSettingsFile
import com.mochyy.mochymusic.shared.settings.playbackPreferences
import java.io.File

internal val desktopPlaybackPreferences by lazy {
    playbackPreferences {
        val home = File(System.getProperty("user.home"))
        val os = System.getProperty("os.name").lowercase()
        val appData = when {
            os.startsWith("mac") -> home.resolve("Library/Application Support")
            os.startsWith("windows") ->
                System.getenv("APPDATA")?.takeUnless(String::isBlank)?.let(::File) ?: home.resolve("AppData/Roaming")
            else -> System.getenv("XDG_CONFIG_HOME")?.let(::File)?.takeIf(File::isAbsolute) ?: home.resolve(".config")
        }
        appData.resolve("Mochy").resolve(PlaybackSettingsFile).absolutePath
    }
}
