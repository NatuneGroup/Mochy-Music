package com.mochyy.mochymusic.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// modules/catalog.json: a row without a link is skipped, the name falls back to it, only https icons are kept
class ModuleCatalogParserTest {

    @Test
    fun everyRowWithALinkIsAnEntry() {
        val entries = parseModuleCatalog(
            """
            {
              "modules": [
                { "name": "Better Lyrics", "description": "Words from the web", "url": "https://lyrics.example/manifest.json",
                  "adds": ["lyrics"], "author": "someone", "icon": "https://lyrics.example/icon.png" },
                { "name": "No link here", "description": "dropped" },
                { "url": "https://looks.example/module.zip", "adds": ["look", "theme", "glow"], "icon": "http://looks.example/icon.png" }
              ]
            }
            """,
        )

        assertEquals(2, entries.size)
        assertEquals("Better Lyrics", entries[0].name)
        assertEquals(listOf("lyrics"), entries[0].adds)
        assertEquals("someone", entries[0].author)
        assertEquals("https://lyrics.example/icon.png", entries[0].icon)
        // a row with no name reads as its link, and an icon that isn't https is left out
        assertEquals("https://looks.example/module.zip", entries[1].name)
        assertEquals(listOf("look", "theme", "glow"), entries[1].adds)
        assertNull(entries[1].author)
        assertNull(entries[1].icon)
    }

    @Test
    fun aCatalogWithoutModulesReadsEmpty() {
        assertEquals(0, parseModuleCatalog("""{ "modules": [] }""").size)
        assertEquals(0, parseModuleCatalog("{}").size)
    }

    @Test
    fun aRowThatIsNotAnObjectIsSkipped() {
        val entries = parseModuleCatalog("""{ "modules": [ 1, "x", { "url": "https://a.example" } ] }""")
        assertEquals(listOf("https://a.example"), entries.map { it.url })
    }
}
