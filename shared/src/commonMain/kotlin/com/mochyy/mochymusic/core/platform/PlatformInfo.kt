// PlatformInfo.kt

package com.mochyy.mochymusic.core.platform

// The little platform facts the changelog and commit screens need: where their cache lives, how a
// github date is written for people, and which app version the changelog opens on.

// the app's version as the store knows it (the same text BuildConfig.VERSION_NAME has)
expect fun platformVersionName(platformContext: Any): String

// the app's private files folder; the changelog cache has always lived in the root of it
expect fun platformFilesDirectory(platformContext: Any): String

// "September 5, 2026" for a release's published_at, null when the text isn't a date
expect fun releaseDateText(iso: String): String?

// "Sep 5, 2026" for a commit's date, null when the text isn't a date
expect fun commitDateText(iso: String): String?
