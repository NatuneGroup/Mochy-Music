package com.mochyy.mochymusic.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// what .github/scripts/release_version.py names releases, read back the way the updater reads them
class UpdateVersionAndAssetTest {

    private fun release(title: String, tag: String) = parseFullVersion("$title $tag")

    @Test
    fun `nightly tags count up and sit between two releases`() {
        val stable = release("Mochy 8.4.3", "v8.4.3")
        val n1 = release("Mochy 8.4.3.1 nightly", "v8.4.3.1")
        val n2 = release("Mochy 8.4.3.2 nightly", "v8.4.3.2")
        val n10 = release("Mochy 8.4.3.10 nightly", "v8.4.3.10")
        val next = release("Mochy 8.4.4", "v8.4.4")
        assertEquals(1, n1.nightly)
        assertTrue(stable < n1)
        assertTrue(n1 < n2)
        assertTrue(n2 < n10)
        assertTrue(n10 < next)
    }

    @Test
    fun `an installed CI build is not offered its own release again`() {
        val installedNightly = parseFullVersion("8.4.3.2 build#29923948")
        assertEquals(29923948, installedNightly.buildNumber)
        assertTrue(release("Mochy 8.4.3.2 nightly", "v8.4.3.2") < installedNightly)
        assertTrue(release("Mochy 8.4.3.3 nightly", "v8.4.3.3") > installedNightly)

        val installedStable = parseFullVersion("8.4.4 build#29930000")
        assertTrue(release("Mochy 8.4.4", "v8.4.4") < installedStable)
        assertTrue(release("Mochy 8.4.4.1 nightly", "v8.4.4.1") > installedStable)
    }

    @Test
    fun `today's local build sees the first published release as newer`() {
        val local = parseFullVersion("8.4.3 build#1290")
        assertTrue(release("Mochy 8.4.4", "v8.4.4") > local)
        assertTrue(release("Mochy 8.4.3.1 nightly", "v8.4.3.1") > local)
    }

    private fun apk(name: String) = ApkAsset(name, "https://example.invalid/$name", 1L)

    @Test
    fun `the flavour comes out of the build's flavour name`() {
        assertEquals("foss", installedVariant("arm64Foss"))
        assertEquals("gms", installedVariant("universalGms"))
        assertEquals("fdroid", installedVariant("x86_64Fdroid"))
        assertNull(installedVariant(""))
    }

    @Test
    fun `picks the apk for this abi and flavour`() {
        val assets = listOf(
            apk("mochy-8.4.4-arm64-gms.apk"),
            apk("mochy-8.4.4-arm64-foss.apk"),
            apk("mochy-8.4.4-arm64-foss.apk.sha256"),
            apk("mochy-8.4.4-universal-foss.apk"),
        )
        assertEquals("mochy-8.4.4-arm64-foss.apk", pickApkAsset(assets, "arm64", "foss")?.name)
        assertEquals("mochy-8.4.4-arm64-gms.apk", pickApkAsset(assets, "arm64", "gms")?.name)
    }

    @Test
    fun `falls back the way the updater always did`() {
        val published = listOf(apk("mochy-8.4.4-arm64-foss.apk"))
        // no gms or fdroid build is published: the arm64 one is still offered
        assertEquals("mochy-8.4.4-arm64-foss.apk", pickApkAsset(published, "arm64", "gms")?.name)
        assertEquals("mochy-8.4.4-arm64-foss.apk", pickApkAsset(published, "arm64", null)?.name)

        val mixed = listOf(apk("other.apk"), apk("mochy-universal.apk"), apk("notes.txt"))
        assertEquals("mochy-universal.apk", pickApkAsset(mixed, "armeabi", "foss")?.name)
        assertEquals("mochy-universal.apk", pickApkAsset(mixed, "universal", "foss")?.name)
        assertEquals("other.apk", pickApkAsset(listOf(apk("other.apk"), apk("b.apk")), "arm64", "foss")?.name)
        assertNull(pickApkAsset(listOf(apk("notes.txt")), "arm64", "foss"))
    }
}
