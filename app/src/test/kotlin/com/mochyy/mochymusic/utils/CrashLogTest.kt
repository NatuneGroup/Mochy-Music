package com.mochyy.mochymusic.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() = CrashLog.clear()

    @After
    fun tearDown() = CrashLog.clear()

    @Test
    fun reportCarriesExceptionAndNewestLinesAfterTheRingWraps() {
        repeat(4005) { i -> CrashLog.record('I', "Test", "<line ${i + 1}>", null) }
        val dir = File(tmp.root, "crash")
        val file = CrashLog.writeReport(
            dir,
            Thread.currentThread(),
            RuntimeException("boom", IllegalStateException("root cause")),
            header = listOf("App: test"),
        ) { it.println("=== appendix ===") }

        assertEquals(dir, file.parentFile)
        assertTrue(file.name.matches(Regex("crash-\\d{8}-\\d{6}\\.log")))
        val text = file.readText()
        assertTrue(text.contains("boom"))
        assertTrue(text.contains("root cause"))
        assertTrue(text.contains("App: test"))
        assertTrue(text.contains("<line 4005>"))
        assertTrue(text.contains("<line 6>"))
        assertFalse(text.contains("<line 5>"))
        assertTrue(text.contains("=== appendix ==="))
        assertTrue(text.indexOf("<line 6>") < text.indexOf("<line 4005>"))
    }

    @Test
    fun appendixFailureDoesNotLoseTheReport() {
        val file = CrashLog.writeReport(tmp.root, null, RuntimeException("boom")) { error("logcat unavailable") }
        assertTrue(file.readText().contains("boom"))
    }

    @Test
    fun keepsOnlyTheNewestReports() {
        repeat(5) { File(tmp.root, "crash-2020010$it-000000.log").writeText("old") }
        CrashLog.writeReport(tmp.root, null, RuntimeException("new"), keep = 3)
        val left = tmp.root.listFiles()!!.map { it.name }.sorted()
        assertEquals(3, left.size)
        assertFalse(left.contains("crash-20200100-000000.log"))
    }
}
