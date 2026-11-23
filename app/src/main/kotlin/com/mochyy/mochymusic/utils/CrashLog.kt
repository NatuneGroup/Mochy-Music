// Ported from Sora by shizenworks (github.com/shizenworks/sora), GPL-3.0, read at 0b1aa4bb.
// Sora's utils/CrashLog.kt, itself from Neiro (github.com/4nx3b/Neiro, com.music.bitchord.data.CrashLog)
// and ArchiveTune. A ring buffer of the app's recent log lines plus the report writer. No android.* so
// it runs in JVM tests.

package com.mochyy.mochymusic.utils

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    internal const val CAPACITY = 4000

    private val buffer: Array<String?> = arrayOfNulls(CAPACITY)
    // one formatter, used only under this object's lock: SimpleDateFormat isn't thread-safe, and
    // building one per log line was most of the cost of recording it
    private val lineTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var index = 0
    private var count = 0

    @Synchronized
    fun record(level: Char, tag: String, message: String, error: Throwable?) {
        val line = buildString {
            append(lineTime.format(Date())).append(' ').append(level).append(' ').append(tag).append(": ").append(message)
            if (error != null) append('\n').append(error.stackTraceString())
        }
        buffer[index] = line
        index = (index + 1) % CAPACITY
        if (count < CAPACITY) count++
    }

    @Synchronized
    fun snapshot(): List<String> {
        val n = count
        if (n == 0) return emptyList()
        val start = if (n >= CAPACITY) index else 0
        return (0 until n).mapNotNull { buffer[(start + it) % CAPACITY] }
    }

    @Synchronized
    internal fun clear() {
        buffer.fill(null)
        index = 0
        count = 0
    }

    fun fileName(date: Date = Date()): String =
        "crash-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(date) + ".log"

    /** writes the report into [dir] synchronously, keeps the newest [keep] reports, and returns the file */
    fun writeReport(
        dir: File,
        thread: Thread?,
        throwable: Throwable,
        header: List<String> = emptyList(),
        keep: Int = 10,
        appendix: (PrintWriter) -> Unit = {},
    ): File {
        dir.mkdirs()
        val file = File(dir, fileName())
        val lines = snapshot()
        file.printWriter().use { out ->
            out.println("=== Mochy crash report ===")
            out.println("Time: ${timestamp()}")
            out.println("Thread: ${thread?.name ?: "?"}")
            header.forEach(out::println)
            out.println()
            out.println("=== Uncaught exception ===")
            throwable.printStackTrace(out)
            out.println()
            out.println("=== Recent app log (last ${lines.size} lines, oldest first) ===")
            lines.forEach(out::println)
            out.flush()
            runCatching { appendix(out) }
        }
        runCatching {
            dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".log") }
                ?.sortedByDescending { it.name }
                ?.drop(keep)
                ?.forEach { it.delete() }
        }
        return file
    }

    /** the newest report in [dir], or null when the app has not crashed yet */
    fun latestReport(dir: File): File? =
        dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".log") }
            ?.maxByOrNull { it.name }

    internal fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun Throwable.stackTraceString(): String =
        StringWriter().also { PrintWriter(it).use { pw -> printStackTrace(pw) } }.toString()
}
