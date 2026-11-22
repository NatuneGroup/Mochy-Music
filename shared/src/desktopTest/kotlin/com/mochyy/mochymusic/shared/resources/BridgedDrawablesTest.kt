package com.mochyy.mochymusic.shared.resources

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// Compose resources only read vector sizes in dp or px and throw at runtime on anything else, which
// crashed the app the first time a shared screen drew an icon sized "24.0dip". Every icon the
// ResourceBridge copies in from app/src/main/res is checked here, in the folder the build reads.
class BridgedDrawablesTest {
    private val drawables = File("build/generated/mochyComposeResources/commonMain/drawable")
    private val size = Regex("""android:(?:width|height)="([^"]*)"""")

    @Test
    fun iconsAreThere() {
        // the bridge runs before the shared code compiles; if this folder moves, the check below must follow it
        assertTrue(drawables.listFiles().orEmpty().any { it.extension == "xml" }, "no bridged icons in ${drawables.absolutePath}")
    }

    @Test
    fun everyIconIsSizedInDpOrPx() {
        val bad = drawables.listFiles().orEmpty().filter { it.extension == "xml" }.flatMap { file ->
            size.findAll(file.readText()).map { it.groupValues[1] }
                .filterNot { it.matches(Regex("""[0-9.]+(dp|px)""")) }
                .map { "${file.name}: $it" }
        }
        assertTrue(bad.isEmpty(), "icons Compose resources can't read: $bad")
    }

    @Test
    fun theSettingsArrowIsBridged() {
        // the icon that crashed Settings → Now Playing
        val text = File(drawables, "navigate_next.xml").readText()
        assertTrue("24.0dp\"" in text && "dip\"" !in text, text)
    }
}
