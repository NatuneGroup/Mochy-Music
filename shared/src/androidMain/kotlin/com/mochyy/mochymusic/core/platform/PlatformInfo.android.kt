// PlatformInfo.android.kt

package com.mochyy.mochymusic.core.platform

import android.content.Context
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// the version name shown in the app's own store listing, the same text BuildConfig.VERSION_NAME carries
actual fun platformVersionName(platformContext: Any): String {
    val context = platformContext as Context
    return runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}

actual fun platformFilesDirectory(platformContext: Any): String = (platformContext as Context).filesDir.absolutePath

// the formatter the changelog has always used, in the phone's own language
actual fun releaseDateText(iso: String): String? = runCatching {
    ZonedDateTime.parse(iso).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
}.getOrNull()

actual fun commitDateText(iso: String): String? = runCatching {
    ZonedDateTime.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
}.getOrNull()
