package com.mochyy.mochymusic.core.updater

import android.content.Context
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.link.linkKeyValueStore

// release only sees normal github releases, nightly sees the prereleases too and takes whichever is newest.
// The channel rules live in :shared (UpdateCatalog/UpdateSettings); this is the Android build's binding.

// a nightly is marked when it's built, or carries a fourth number in its version (8.3.2.4)
val installedIsNightly: Boolean by lazy {
    BuildConfig.IS_NIGHTLY || parseFullVersion(BuildConfig.VERSION_NAME).nightly > 0
}

private fun updateSettings(context: Context) = UpdateSettings(linkKeyValueStore(context, UpdateSettings.Prefs))

// a nightly build starts out on the nightly channel, everything else on release, until someone switches
fun updateChannel(context: Context): UpdateChannel = updateSettings(context).channel(installedIsNightly)

fun saveUpdateChannel(context: Context, channel: UpdateChannel) = updateSettings(context).saveChannel(channel)

// leaving nightly would mean installing an older release over it, which can break the library, so it waits for a newer release
fun canLeaveNightly(latestRelease: GithubRelease?): Boolean = catalog.canLeaveNightly(latestRelease)
