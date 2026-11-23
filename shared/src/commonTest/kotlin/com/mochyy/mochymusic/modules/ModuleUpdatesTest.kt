package com.mochyy.mochymusic.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// where a module came from, and whether a newer copy waits there
class ModuleUpdatesTest {

    private val releaseUrl = "https://api.github.com/repos/someone/module/releases/latest"

    private fun release(tag: String, stamp: Long = 1) = ModuleResponse(
        200,
        """{ "tag_name": "$tag", "assets": [ { "id": $stamp, "name": "mochyymodule.zip", "browser_download_url": "https://example.com/m.zip", "size": 4 } ] }""".toByteArray(),
    )

    @Test
    fun aGithubSourceIsKeptAndReadBack() {
        val store = FakeModuleStore()
        val updates = ModuleUpdates(store, FakeModuleHttp())

        updates.installed("lyrics", ModuleSource.GitHub("someone", "module", "v1", "1:2026-01-01:4"))
        assertEquals(ModuleSource.GitHub("someone", "module", "v1", "1:2026-01-01:4"), updates.source("lyrics"))
        assertTrue(updates.checkedAt("lyrics")!! > 0L)
        assertNull(updates.update("lyrics"))

        updates.forget("lyrics")
        assertNull(updates.source("lyrics"))
        assertNull(updates.checkedAt("lyrics"))
    }

    @Test
    fun aLinkSourceKeepsItsFingerprint() {
        val store = FakeModuleStore()
        val updates = ModuleUpdates(store, FakeModuleHttp())
        updates.installed("look", ModuleSource.Link("https://looks.example", "aaf4c61ddcc5e8a2dabe"))
        assertEquals(ModuleSource.Link("https://looks.example", "aaf4c61ddcc5e8a2dabe"), updates.source("look"))
        assertEquals("looks.example", updates.source("look")!!.label)
        assertEquals("https://looks.example", updates.source("look")!!.link)
        // a zip from the phone has no source and stops being checked
        updates.installed("look", null)
        assertNull(updates.source("look"))
    }

    @Test
    fun aNewerReleaseIsNoticedAndTheSameOneIsNot() {
        val store = FakeModuleStore()
        val http = FakeModuleHttp(mapOf(releaseUrl to release("v2")))
        val updates = ModuleUpdates(store, http)
        updates.installed("module", ModuleSource.GitHub("someone", "module", "v1", "1"))

        updates.check("module", installedVersion = "1", force = true, preferDebug = false)
        assertEquals(ModuleUpdate("v2"), updates.update("module"))

        // the same release as the one installed is no update at all
        updates.installed("module", ModuleSource.GitHub("someone", "module", "v2", "1::4"))
        updates.check("module", installedVersion = "2", force = true, preferDebug = false)
        assertNull(updates.update("module"))
    }

    @Test
    fun theSameTagWithAFreshlyUploadedFileIsNewer() {
        val store = FakeModuleStore()
        val http = FakeModuleHttp(mapOf(releaseUrl to release("v1", stamp = 99)))
        val updates = ModuleUpdates(store, http)
        updates.installed("module", ModuleSource.GitHub("someone", "module", "v1", "1"))
        updates.check("module", installedVersion = "1", force = true, preferDebug = false)
        assertEquals(ModuleUpdate("v1"), updates.update("module"))
    }

    @Test
    fun aCheckIsSkippedForSixHoursUnlessForced() {
        val store = FakeModuleStore()
        val http = FakeModuleHttp(mapOf(releaseUrl to release("v2")))
        val updates = ModuleUpdates(store, http)
        updates.installed("module", ModuleSource.GitHub("someone", "module", "v1", "1"))

        updates.check("module", "1", force = false, preferDebug = false)
        assertEquals(0, http.asked.size)
        updates.check("module", "1", force = true, preferDebug = false)
        assertEquals(1, http.asked.size)
        updates.check("module", "1", force = false, preferDebug = false)
        assertEquals(1, http.asked.size)
    }

    @Test
    fun aLinkWhoseManifestChangedIsANewerCopy() {
        val store = FakeModuleStore()
        val base = "https://looks.example"
        val http = FakeModuleHttp(
            mapOf(
                "$base/manifest.json" to ModuleResponse(200, """{ "id": "glass", "name": "Glass", "version": "2" }""".toByteArray()),
            ),
        )
        val updates = ModuleUpdates(store, http)
        updates.installed("glass", ModuleSource.Link(base, ModuleUpdates.fingerprint("""{ "id": "glass", "name": "Glass", "version": "1" }""")))

        updates.check("glass", installedVersion = "1", force = true, preferDebug = false)
        assertEquals(ModuleUpdate("2"), updates.update("glass"))

        // a manifest that isn't one leaves what was known before alone
        val broken = FakeModuleHttp(mapOf("$base/manifest.json" to ModuleResponse(200, """{ "hello": "there" }""".toByteArray())))
        val same = ModuleUpdates(store, broken)
        same.check("glass", installedVersion = "1", force = true, preferDebug = false)
        assertEquals(ModuleUpdate("2"), same.update("glass"))
    }

    @Test
    fun aSourceThatCannotBeReadKeepsWhatWasKnown() {
        val store = FakeModuleStore()
        val updates = ModuleUpdates(store, FakeModuleHttp())
        updates.installed("module", ModuleSource.Link("https://looks.example", "abc"))
        updates.check("module", installedVersion = "1", force = true, preferDebug = false)
        assertNull(updates.update("module"))
        assertEquals("abc", (updates.source("module") as ModuleSource.Link).fingerprint)
    }

    @Test
    fun aFingerprintIsTheFirstTenBytesOfTheSha1OfTheTrimmedText() {
        assertEquals("aaf4c61ddcc5e8a2dabe", ModuleUpdates.fingerprint("  hello\n"))
        assertEquals(20, ModuleUpdates.fingerprint("anything").length)
    }
}
