package com.mochyy.mochymusic.core.updater

import android.content.Context
import com.mochyy.mochymusic.link.linkKeyValueStore

// The update settings and helpers the version sheet and launch check share. The keys, defaults and
// the "settings" preferences file live in :shared (UpdateSettings); this is the Android binding.
// The checking itself is UpdateCatalog.kt in :shared; the older full-screen updater that used to live
// here had no route left.

private fun settings(context: Context) = UpdateSettings(linkKeyValueStore(context, UpdateSettings.Prefs))

fun getUpdateAvailableState(context: Context): Boolean = settings(context).updateAvailable()

fun saveUpdateAvailableState(context: Context, available: Boolean) = settings(context).saveUpdateAvailable(available)

fun getAutoUpdateCheckSetting(context: Context): Boolean = settings(context).autoUpdateCheck()

fun saveAutoUpdateCheckSetting(context: Context, enabled: Boolean) = settings(context).saveAutoUpdateCheck(enabled)

fun getUpdateNotificationsSetting(context: Context): Boolean = settings(context).updateNotifications()

fun saveUpdateNotificationsSetting(context: Context, enabled: Boolean) = settings(context).saveUpdateNotifications(enabled)

fun saveLastCheckedTime(context: Context, timestamp: String) = settings(context).saveLastCheckedTime(timestamp)

fun getLastCheckedTime(context: Context): String = settings(context).lastCheckedTime()

fun getBetaUpdatesSetting(context: Context): Boolean = settings(context).betaUpdates()

fun saveBetaUpdatesSetting(context: Context, enabled: Boolean) = settings(context).saveBetaUpdates(enabled)

// isNewerVersion and extractUrls live in :shared (core/updater), with the screens that use them.
