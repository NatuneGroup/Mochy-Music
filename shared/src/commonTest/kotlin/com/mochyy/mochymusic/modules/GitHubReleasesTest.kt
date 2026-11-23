package com.mochyy.mochymusic.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** a module store that lives in memory, the way SharedPreferences behaves for strings, longs and booleans */
internal class FakeModuleStore(initial: Map<String, String> = emptyMap()) : ModuleStore {
    private val strings = initial.toMutableMap()
    private val longs = mutableMapOf<String, Long>()
    private val booleans = mutableMapOf<String, Boolean>()

    override fun string(key: String): String? = strings[key]
    override fun long(key: String, fallback: Long): Long = longs[key] ?: fallback
    override fun boolean(key: String, fallback: Boolean): Boolean = booleans[key] ?: fallback
    override fun putString(key: String, value: String?) {
        if (value == null) strings.remove(key) else strings[key] = value
    }

    override fun putLong(key: String, value: Long) {
        longs[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }

    override fun remove(key: String) {
        strings.remove(key)
        longs.remove(key)
        booleans.remove(key)
    }

    override fun contains(key: String): Boolean = key in strings || key in longs || key in booleans
}

/** a web that answers what the test laid out, and remembers what was asked */
internal class FakeModuleHttp(private val routes: Map<String, ModuleResponse> = emptyMap()) : ModuleHttp {
    val asked = mutableListOf<String>()

    override fun get(url: String, headers: Map<String, String>, maxBytes: Long): ModuleResponse? {
        asked += url
        return routes[url]
    }
}

// a github link read as a project and a release, and the zip picked out of it
class GitHubReleasesTest {

    @Test
    fun aProjectLinkIsReadWithItsReleasesAndTags() {
        assertEquals(GitHubReleases.Link("realidkroo", "mochy-music", null), GitHubReleases.parse("https://github.com/realidkroo/mochy-music"))
        assertEquals(GitHubReleases.Link("realidkroo", "mochy-music", null), GitHubReleases.parse("github.com/realidkroo/mochy-music/releases/latest"))
        assertEquals(GitHubReleases.Link("realidkroo", "mochy-music", "v1.2"), GitHubReleases.parse("https://www.github.com/realidkroo/mochy-music/releases/tag/v1.2/"))
        assertEquals(GitHubReleases.Link("someone", "module", null), GitHubReleases.parse("https://github.com/someone/module.git"))
    }

    @Test
    fun aFileLinkOrAnotherSiteStaysAPlainLink() {
        assertNull(GitHubReleases.parse("https://github.com/someone/module/blob/main/module.zip"))
        assertNull(GitHubReleases.parse("https://github.com/someone/module/raw/main/manifest.json"))
        assertNull(GitHubReleases.parse("https://gitlab.com/someone/module"))
        assertNull(GitHubReleases.parse("https://github.com/someone"))
    }

    @Test
    fun theNewestReleaseNamesItsModuleZip() {
        val http = FakeModuleHttp(
            mapOf(
                "https://api.github.com/repos/someone/module/releases/latest" to ModuleResponse(
                    200,
                    """
                    { "tag_name": "v3", "assets": [
                        { "id": 1, "name": "notes.txt", "browser_download_url": "https://example.com/notes.txt", "size": 10 },
                        { "id": 2, "name": "mochyymodule-release.zip", "browser_download_url": "https://example.com/release.zip", "size": 1024, "updated_at": "2026-01-02" }
                    ] }
                    """.trimIndent().toByteArray(),
                ),
            ),
        )
        val found = GitHubReleases.release(http, GitHubReleases.Link("someone", "module", null), preferDebug = false)
        val newest = found as GitHubReleases.Found.Newest
        assertEquals("v3", newest.release.tag)
        assertEquals("mochyymodule-release.zip", newest.release.asset)
        assertEquals("https://example.com/release.zip", newest.release.url)
        assertEquals("2:2026-01-02:1024", newest.release.stamp)
    }

    @Test
    fun aReleaseWithoutAModuleZipOrWithoutAnyReleaseSaysSo() {
        val none = FakeModuleHttp(mapOf("https://api.github.com/repos/a/b/releases/latest" to ModuleResponse(404, ByteArray(0))))
        assertEquals(GitHubReleases.Found.NoRelease, GitHubReleases.release(none, GitHubReleases.Link("a", "b", null), preferDebug = false))

        val other = FakeModuleHttp(
            mapOf("https://api.github.com/repos/a/b/releases/latest" to ModuleResponse(200, """{ "assets": [ { "name": "readme.md" } ] }""".toByteArray())),
        )
        assertEquals(GitHubReleases.Found.NoModule, GitHubReleases.release(other, GitHubReleases.Link("a", "b", null), preferDebug = false))

        val down = FakeModuleHttp()
        assertEquals(GitHubReleases.Found.Unreachable, GitHubReleases.release(down, GitHubReleases.Link("a", "b", null), preferDebug = false))
    }

    @Test
    fun aDownloadedZipComesBackWithItsBytesAndWhereItCameFrom() {
        val http = FakeModuleHttp(
            mapOf(
                "https://api.github.com/repos/someone/module/releases/latest" to ModuleResponse(
                    200,
                    """{ "tag_name": "v3", "assets": [ { "name": "mochyymodule.zip", "browser_download_url": "https://example.com/m.zip", "size": 4 } ] }""".toByteArray(),
                ),
                "https://example.com/m.zip" to ModuleResponse(200, byteArrayOf(1, 2, 3, 4)),
            ),
        )
        val zip = GitHubReleases.download(http, GitHubReleases.Link("someone", "module", null), preferDebug = false) as GitHubReleases.Found.Zip
        assertEquals("mochyymodule.zip", zip.name)
        assertTrue(byteArrayOf(1, 2, 3, 4).contentEquals(zip.bytes))
        assertEquals("github.com/someone/module · v3", zip.from)
    }
}
