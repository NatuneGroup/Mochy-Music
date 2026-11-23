package com.mochyy.mochymusic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashExportTest {
    @Test
    fun latestReportIsTheNewestCrashFile() {
        val dir = Files.createTempDirectory("crash").toFile()
        File(dir, "crash-20260101-000000.log").writeText("a")
        File(dir, "crash-20260102-120000.log").writeText("b")
        File(dir, "notes.txt").writeText("c")

        assertEquals("crash-20260102-120000.log", CrashLog.latestReport(dir)?.name)
    }

    @Test
    fun latestReportIgnoresForeignFilesAndFolders() {
        val dir = Files.createTempDirectory("crash").toFile()
        File(dir, "notes.txt").writeText("a")
        File(dir, "crash-20990101-000000.log").mkdirs()

        assertNull(CrashLog.latestReport(dir))
    }

    @Test
    fun latestReportIsNullWhenNothingCrashed() {
        assertNull(CrashLog.latestReport(File("/nonexistent-crash-dir")))
    }

    @Test
    fun latestReportFindsTheReportJustWritten() {
        val dir = Files.createTempDirectory("crash").toFile()
        val report = CrashLog.writeReport(dir, Thread.currentThread(), RuntimeException("boom"))

        assertEquals(report, CrashLog.latestReport(dir))
    }
}
