package com.mochyy.mochymusic.core.updater

import android.content.Context
import java.io.File

// The APK folder and its clean-up live in :shared (UpdateDownloads); this keeps the File shape the
// settings screen has always used.

fun getDownloadedApksDir(context: Context): File = File(UpdateDownloads.directoryPath(context))

fun getDownloadedApkCount(context: Context): Int = UpdateDownloads.count(context)

fun clearDownloadedApks(context: Context): Boolean = UpdateDownloads.clear(context)

fun autoClearOldApks(context: Context) = UpdateDownloads.clearOld(context)
