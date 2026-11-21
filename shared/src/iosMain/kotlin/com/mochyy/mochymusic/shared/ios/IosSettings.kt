package com.mochyy.mochymusic.shared.ios

import com.mochyy.mochymusic.shared.settings.PlaybackSettingsFile
import com.mochyy.mochymusic.shared.settings.playbackPreferences
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal val iosPlaybackPreferences by lazy {
    playbackPreferences {
        val directory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        "${checkNotNull(directory?.path)}/$PlaybackSettingsFile"
    }
}
