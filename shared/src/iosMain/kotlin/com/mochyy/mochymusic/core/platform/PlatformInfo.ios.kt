// PlatformInfo.ios.kt

package com.mochyy.mochymusic.core.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSHomeDirectory

// the bundle's short version and the app's documents folder, next to where the rest of iOS keeps its files
actual fun platformVersionName(platformContext: Any): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
        ?.takeIf { it.isNotBlank() } ?: "0.0.0"

actual fun platformFilesDirectory(platformContext: Any): String = NSHomeDirectory() + "/Documents"

actual fun platformDownloadsDirectory(platformContext: Any): String = NSHomeDirectory() + "/Documents/Downloads"
