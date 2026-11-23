package com.mochyy.mochymusic.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// every switch, pick, setting and date the registry keeps, under the keys it always used
class ModulePreferencesTest {

    private val store = FakeModuleStore()
    private val prefs = ModulePreferences(store)

    @Test
    fun aModuleStartsOffUnlessItsFallbackSaysOtherwise() {
        assertEquals(false, prefs.isEnabled("applemusic"))
        // youtube is on until it's turned off
        assertEquals(true, prefs.isEnabled("youtube", fallback = true))
        prefs.setEnabled("applemusic", true)
        assertEquals(true, prefs.isEnabled("applemusic"))
        assertEquals(true, store.boolean("enabled_applemusic"))
    }

    @Test
    fun onlyThePicksThatWereMadeComeBack() {
        prefs.setPick("applemusic", "quality", "lossless")
        assertEquals(mapOf("quality" to "lossless"), prefs.picks("applemusic", listOf("quality", "region")))
        // a choice nobody touched has no entry at all: its first option stands
        assertEquals(emptyMap(), prefs.picks("jiosaavn", listOf("quality")))
        assertEquals("lossless", store.string("pick_applemusic_quality"))
    }

    @Test
    fun settingsKeepTheirDefaultUntilSomeoneChangesThemAndKeepTheFieldsOrder() {
        val defaults = linkedMapOf("token" to "", "region" to "us", "pureBlack" to "false")
        assertEquals(defaults, prefs.fieldValues("glass", defaults))

        prefs.setField("glass", "region", "jp")
        assertEquals("jp", prefs.fieldValues("glass", defaults)["region"])
        assertEquals(listOf("token", "region", "pureBlack"), prefs.fieldValues("glass", defaults).keys.toList())
        assertEquals("jp", store.string("field_glass_region"))
    }

    @Test
    fun theFirstAddedDateStaysAndEveryUpdateMovesTheInstalledOne() {
        assertNull(prefs.addedAt("look"))
        assertNull(prefs.installedAt("look"))

        prefs.markAdded("look", 1000L)
        assertEquals(1000L, prefs.addedAt("look"))
        // a copy from before the installed date was kept counts from when it was first added
        assertEquals(1000L, prefs.installedAt("look"))

        prefs.markInstalled("look", 2000L)
        prefs.markAdded("look", 3000L)
        assertEquals(1000L, prefs.addedAt("look"))
        assertEquals(2000L, prefs.installedAt("look"))
    }

    @Test
    fun theOrderFallsBackToTheProviderOrderAndNewOnesGoAfterTheOnesItLists() {
        assertEquals(emptyList(), prefs.order())
        store.putString("provider_order", "monochrome,applemusic,youtube")
        assertEquals(listOf("monochrome", "applemusic", "youtube"), prefs.order())

        prefs.setOrder(listOf("look"))
        assertEquals(listOf("look", "monochrome", "applemusic", "youtube"), prefs.order())
        // what was dragged on the modules page wins over the provider order from then on
        store.putString("provider_order", "somethingelse")
        assertEquals(listOf("look", "monochrome", "applemusic", "youtube"), prefs.order())
    }

    @Test
    fun thePlayersPickIsEmptyBeforeTheFirstOne() {
        assertEquals("", prefs.playerPick())
        prefs.setPlayerPick("vinyl/main")
        assertEquals("vinyl/main", prefs.playerPick())
        assertEquals("vinyl/main", store.string("player_pick"))
    }

    @Test
    fun takingAModuleAwayForgetsEverythingAboutItButTheOrder() {
        prefs.setEnabled("look", true)
        prefs.setPick("look", "style", "clear")
        prefs.setField("look", "fringe", "true")
        prefs.markAdded("look", 1000L)
        prefs.markInstalled("look", 2000L)
        prefs.setOrder(listOf("look"))

        prefs.forget("look", choiceIds = listOf("style"), fieldIds = listOf("fringe"))

        assertTrue(!store.contains("enabled_look"))
        assertNull(store.string("pick_look_style"))
        assertNull(store.string("field_look_fringe"))
        assertNull(prefs.addedAt("look"))
        assertNull(prefs.installedAt("look"))
        assertEquals(listOf("look"), prefs.order())
    }
}
