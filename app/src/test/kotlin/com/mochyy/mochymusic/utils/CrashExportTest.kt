package com.mochyy.mochymusic.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @After
    fun clearRing() = CrashLog.clear()

    @Test
    fun exportedReportHasNoAccountOrImportDetails() {
        // fake values in the places a real report carries them: the log ring, the exception and logcat
        val accountName = "Jane Fakeperson"
        val email = "jane.fake@example.com"
        val handle = "@janefake_channel"
        val cookie = "SAPISID=AbCdEf123456/FAKEcookieValue; __Secure-3PSID=g.a000FAKEsidValueXYZ"
        val listenBrainz = "5f1e8c3a-fake-4b2d-9e7f-1a2b3c4d5e6f"
        val openRouter = "sk-or-v1-0123456789abcdef0123456789abcdef0123456789abcdef"
        val stream = "https://rr3---sn-fake.googlevideo.com/videoplayback?expire=1&sig=FAKESIG123&ip=203.0.113.7"
        val imported = "content://com.android.providers.downloads.documents/document/msf%3A1234"
        val importPath = "/storage/emulated/0/Download/Jane's liked songs.csv"
        val playlist = "https://open.spotify.com/playlist/37i9dQZF1DXfakePlaylist?si=FAKESHARE"

        CrashLog.record('I', "Account", "signed in as $accountName <$email> $handle", null)
        CrashLog.record('I', "InnerTube", "cookie: $cookie", null)
        CrashLog.record('W', "ListenBrainz", "submit failed, token=$listenBrainz", null)
        CrashLog.record('E', "Player", "stream failed $stream", null)
        CrashLog.record('I', "Import", "importing $imported from $importPath", null)
        CrashLog.record('I', "Import", "spotify playlist $playlist", null)
        val dir = Files.createTempDirectory("crash").toFile()
        val report = CrashLog.writeReport(
            dir,
            Thread.currentThread(),
            IllegalStateException("translate failed with Authorization: Bearer $openRouter for $email"),
        ) { out -> out.println("=== logcat ===\n11-23 15:00:00 I Account: $accountName") }

        val copy = CrashRedactor.exportCopy(report, Files.createTempDirectory("share").toFile(), listOf(accountName, handle, listenBrainz, openRouter))
        val text = copy.readText()

        listOf(
            accountName, email, handle, "AbCdEf123456", "FAKEcookieValue", "FAKEsidValueXYZ", listenBrainz, openRouter,
            "FAKESIG123", "203.0.113.7", "videoplayback", "msf%3A1234", "liked songs", "37i9dQZF1DXfakePlaylist", "FAKESHARE",
        ).forEach { secret -> assertFalse("\"$secret\" is still in the exported report", text.contains(secret, ignoreCase = true)) }

        // what makes the report worth sending is still there
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("at com.mochyy.mochymusic.utils.CrashExportTest"))
        assertTrue(text.contains("https://rr3---sn-fake.googlevideo.com/"))
        assertTrue(text.contains("stream failed"))
        // the saved report itself is not touched
        assertTrue(report.readText().contains(accountName))
        assertEquals(report.name, copy.name)
    }

    @Test
    fun redactingLeavesOrdinaryLinesAlone() {
        val line = "2026-11-23 15:00:00.000 I MusicService: queue moved to 3 of 12 (MusicService.kt:412)"
        assertEquals(line, CrashRedactor.redact(line))
    }
}
