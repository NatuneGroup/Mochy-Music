// UpdateCatalogTest.kt

package com.mochyy.mochymusic.core.updater

import com.mochyy.mochymusic.link.LinkKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The rules the version sheet depends on, checked against the versions the app has shipped.
class UpdateCatalogTest {
    @Test
    fun fullVersionReadsReleaseAndNightly() {
        assertEquals(FullVersion(8, 3, 2), parseFullVersion("v8.3.2"))
        assertEquals(FullVersion(8, 3, 2, nightly = 4), parseFullVersion("8.3.2.4"))
        assertEquals(FullVersion(8, 4, 4, buildNumber = 12), parseFullVersion("Mochy 8.4.4 (build#12)"))
        assertEquals(FullVersion(), parseFullVersion("nightly"))
    }

    @Test
    fun nightlySortsAfterTheReleaseItWasBuiltOn() {
        val release = parseFullVersion("8.3.2")
        val first = parseFullVersion("8.3.2.1")
        val second = parseFullVersion("8.3.2.2")
        val next = parseFullVersion("8.3.3")
        assertTrue(first > release)
        assertTrue(second > first)
        assertTrue(next > second)
    }

    @Test
    fun installedVariantComesOutOfTheFlavour() {
        assertEquals("foss", installedVariant("arm64Foss"))
        assertEquals("gms", installedVariant("arm64Gms"))
        assertEquals("fdroid", installedVariant("Fdroid"))
        assertNull(installedVariant("universal"))
    }

    @Test
    fun apkPickingPrefersArchAndVariantThenAnyMatchingArch() {
        val assets = listOf(
            ApkAsset("mochy-8.4.4-universal.apk", "u", 1),
            ApkAsset("mochy-8.4.4-arm64-foss.apk", "af", 2),
            ApkAsset("mochy-8.4.4-arm64-gms.apk", "ag", 3),
            ApkAsset("notes.txt", "n", 0),
        )
        assertEquals("af", pickApkAsset(assets, "arm64", "foss")?.url)
        assertEquals("ag", pickApkAsset(assets, "arm64", null)?.url)
        assertEquals("u", pickApkAsset(assets, "x86", null)?.url)
        assertNull(pickApkAsset(emptyList(), "arm64", "foss"))
    }

    @Test
    fun newerVersionComparisonKeepsItsBetaRules() {
        assertTrue(isNewerVersion("1.2.4", "1.2.3"))
        assertFalse(isNewerVersion("1.2.3", "1.2.3"))
        assertFalse(isNewerVersion("v8.3.2", "8.3.2"))
        assertFalse(isNewerVersion("8.3.2", "v8.3.2"))
        assertTrue(isNewerVersion("b1.2.3", "b1.2.2"))
    }

    @Test
    fun channelDefaultsFollowTheBuildAndWhatWasSaved() {
        val store = FakeStore()
        val settings = UpdateSettings(store)
        assertEquals(UpdateChannel.Release, settings.channel(installedIsNightly = false))
        assertEquals(UpdateChannel.Nightly, settings.channel(installedIsNightly = true))
        settings.saveChannel(UpdateChannel.Release)
        assertEquals(UpdateChannel.Release, settings.channel(installedIsNightly = true))
    }

    @Test
    fun updateSwitchesKeepTheirDefaults() {
        val settings = UpdateSettings(FakeStore())
        assertTrue(settings.autoUpdateCheck())
        assertTrue(settings.updateNotifications())
        assertFalse(settings.betaUpdates())
        assertFalse(settings.updateAvailable())
        assertEquals("", settings.lastCheckedTime())
    }

    @Test
    fun leavingNightlyNeedsANewerRelease() {
        val nightly = UpdateCatalog(UpdateBuild("8.3.2.4", "arm64", "foss", isNightly = true))
        val release = GithubRelease("", "", "8.3.2", "", "", null, null, 0, "")
        assertFalse(nightly.canLeaveNightly(release))
        assertFalse(nightly.canLeaveNightly(null))
        assertTrue(nightly.canLeaveNightly(release.copy(version = "8.3.3")))
        val stable = UpdateCatalog(UpdateBuild("8.3.2", "arm64", "foss", isNightly = false))
        assertTrue(stable.canLeaveNightly(null))
    }

    private class FakeStore : LinkKeyValueStore {
        private val strings = mutableMapOf<String, String>()
        private val booleans = mutableMapOf<String, Boolean>()

        override fun getString(key: String): String? = strings[key]

        override fun putString(key: String, value: String) {
            strings[key] = value
        }

        override fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default

        override fun putBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }

        override fun remove(key: String) {
            strings.remove(key)
            booleans.remove(key)
        }
    }
}
