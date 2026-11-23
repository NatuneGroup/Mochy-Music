package com.mochyy.mochymusic.modules.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// where a path in a module's manifest points: a link as it is, a file in the module's zip, or a file beside a link module's manifest
class ModuleFilesTest {

    private class FakeFiles : ModuleFiles {
        val asked = mutableListOf<String>()
        override fun asset(inside: String): Any = "file:$inside".also { asked += inside }
        override fun path(asset: Any): String? = asset as? String
    }

    @Test
    fun linksStayLinksAndNeverAskForAFile() {
        val files = FakeFiles()
        assertEquals("https://example.com/icon.png", resolveModulePath("https://example.com/icon.png", files, "https://example.com/liquid"))
        assertEquals("http://example.com/a.webp", resolveModulePath("http://example.com/a.webp", null, null))
        assertEquals(emptyList(), files.asked)
    }

    @Test
    fun aPathInAZipIsAFileInTheModuleFolder() {
        val files = FakeFiles()
        assertEquals("file:images/cover.webp", resolveModulePath("images/cover.webp", files, "https://example.com/liquid"))
        assertEquals("file:images/cover.webp", resolveModulePath("/images/cover.webp", files, null))
        assertEquals(listOf("images/cover.webp", "images/cover.webp"), files.asked)
    }

    @Test
    fun aLinkModuleResolvesNextToItsManifest() {
        assertEquals("https://example.com/liquid/icon.png", resolveModulePath("icon.png", null, "https://example.com/liquid"))
        assertNull(resolveModulePath("icon.png", null, null))
    }

    @Test
    fun aPathCannotClimbOutOfTheModule() {
        val files = FakeFiles()
        assertNull(resolveModulePath("../secret.png", files, null))
        assertNull(resolveModulePath("images/../../secret.png", files, null))
        assertNull(resolveModulePath("   ", files, null))
        assertNull(resolveModulePath(null, files, null))
        assertEquals(emptyList(), files.asked)
    }

    @Test
    fun artComesBackAsTextForTheModuleApi() {
        val files = FakeFiles()
        assertEquals("https://example.com/liquid/banner.webp", resolveModuleArt("banner.webp", null, "https://example.com/liquid"))
        assertEquals("file:icon.png", resolveModuleArt("icon.png", files, null))
        assertNull(resolveModuleArt("../secret.png", files, null))
    }

    @Test
    fun aFontOrShaderOnlyComesFromAZip() {
        val files = FakeFiles()
        assertEquals("file:fonts/Lora.ttf", moduleFile("fonts/Lora.ttf", files))
        assertNull(moduleFile("fonts/Lora.ttf", null))
        assertNull(moduleFile("https://example.com/font.ttf", files))
    }
}
