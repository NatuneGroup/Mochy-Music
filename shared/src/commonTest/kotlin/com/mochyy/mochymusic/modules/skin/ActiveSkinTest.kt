package com.mochyy.mochymusic.modules.skin

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// how the looks that are on turn into the picker's styles and the pages that take mochy's over
class ActiveSkinTest {

    private fun player(name: String, id: String = "main", variants: List<SkinVariant> = emptyList()) = SkinPlayer(
        name = name,
        background = SkinBackground.Fill(listOf(SkinColor.Ink)),
        dim = 0f,
        padding = 0.dp,
        align = SkinAlign.Start,
        blocks = emptyList(),
        id = id,
        variants = variants,
    )

    private fun skin(id: String, players: List<SkinPlayer> = emptyList(), pages: List<SkinPage> = emptyList()) =
        ModuleSkin(id, id.uppercase(), players, font = null, pages = pages, files = null, base = null)

    private fun values(id: String, settings: Map<String, String> = emptyMap()) = LookValues(skin(id), settings)

    @Test
    fun everyPlayerIsAStyleAndItsSubStylesSitUnderIt() {
        val dark = SkinVariant("dark", "Dark", player("Vinyl", id = "dark"), mapOf("glass" to "on"))
        val vinyl = player("Vinyl", variants = listOf(dark))
        val radio = player("Radio", id = "radio")
        val styles = ActiveSkin.stylesOf(listOf(LookValues(skin("vinyl", listOf(vinyl, radio)), mapOf("tone" to "warm"))))

        assertEquals(listOf("vinyl/main", "vinyl/main/dark", "vinyl/radio"), styles.map { it.key })
        // a sub-style is the player's group with its own name under it
        assertEquals("Vinyl", styles[0].name)
        assertEquals("vinyl/main", styles[1].group)
        assertEquals("Vinyl · Dark", styles[1].title)
        // the module's settings come along, with the sub-style's own laid over them
        assertEquals(mapOf("tone" to "warm"), styles[0].values)
        assertEquals(mapOf("tone" to "warm", "glass" to "on"), styles[1].values)
        assertTrue(styles.all { it.skin.moduleId == "vinyl" })
    }

    @Test
    fun theLooksKeepTheirOrderSoTheHighestOneComesFirst() {
        val styles = ActiveSkin.stylesOf(listOf(values("top"), values("under")))
        assertEquals(listOf("top", "under"), styles.map { it.skin.moduleId })
    }

    @Test
    fun aPageThatTakesOneOfMochysPagesIsListedUnderEveryNameItTakes() {
        val page = SkinPage("about", "About", listOf(PageBlock.Heading("hi")), replaces = listOf("album", "local_playlist"))
        val pages = ActiveSkin.takeoversOf(listOf(LookValues(skin("vinyl", pages = listOf(page)), mapOf("when" to "x"))))

        assertEquals(setOf("album", "local_playlist"), pages.keys)
        assertEquals(page, pages.getValue("album").single().page)
        assertEquals(mapOf("when" to "x"), pages.getValue("album").single().values)
    }

    @Test
    fun whereTwoLooksTakeTheSamePageTheHigherOneIsFirst() {
        val top = SkinPage("about", "About", listOf(PageBlock.Heading("top")), replaces = listOf("album"))
        val under = SkinPage("about2", "About", listOf(PageBlock.Heading("under")), replaces = listOf("album"))
        val pages = ActiveSkin.takeoversOf(
            listOf(
                LookValues(skin("top", pages = listOf(top)), emptyMap()),
                LookValues(skin("under", pages = listOf(under)), emptyMap()),
            ),
        )
        assertEquals(listOf("top", "under"), pages.getValue("album").map { it.skin.moduleId })
    }

    @Test
    fun aSkinWithNothingToStyleStillParsesButDoesNotRestyleTheApp() {
        val plain = skin("plain", pages = listOf(SkinPage("about", "About", listOf(PageBlock.Heading("hi")))))
        assertEquals(false, plain.restyles)
        assertEquals(0, ActiveSkin.stylesOf(listOf(LookValues(plain, emptyMap()))).size)
        assertEquals(0, ActiveSkin.takeoversOf(listOf(LookValues(plain, emptyMap()))).size)
    }
}
