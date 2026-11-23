// CrashHandler.kt
//
// Mochy's one crash handler. The report writer and the log ring it records into are ported from
// Sora by shizenworks (GPL-3.0), see CrashLog.kt for the provenance line.

package com.mochyy.mochymusic.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.R
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashLog = buildCrashLog(throwable)
            // the full report (header, stack, recent log, logcat) stays in private storage; the settings
            // row hands a copy to the share sheet only when the user picks where it goes
            runCatching {
                CrashLog.writeReport(crashDir(applicationContext), thread, throwable, header()) { appendLogcat(it) }
            }
            Timber.e(throwable, "App crashed")
            // in release Timber's only tree is the in-memory ring, so this is what reaches logcat, and
            // R8 keeps it. The process is killed right after, so it is also the last thing in logcat.
            android.util.Log.e("MochyCrash", crashLog)
            // and on the phone, because logcat has long moved on by the time a crash gets looked at.
            // Read it back with `adb shell run-as <package> cat files/last_crash.txt`.
            runCatching { File(applicationContext.filesDir, LAST_CRASH_FILE).writeText(crashLog) }

            Process.killProcess(Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {

            Timber.e(e, "Error handling crash")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashLog(throwable: Throwable): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        return buildString {
            appendLine("mochy Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
        }
    }

    private fun header(): List<String> = listOf(
        "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
        "Package: ${applicationContext.packageName}",
        "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
        "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}",
    )

    private fun appendLogcat(out: PrintWriter) {
        out.println()
        out.println("=== logcat (this process, last $LOGCAT_LINES lines) ===")
        val text = StringBuilder()
        val process = runCatching {
            ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", LOGCAT_LINES.toString(), "--pid=${Process.myPid()}")
                .redirectErrorStream(true).start()
        }.getOrElse {
            out.println("(logcat unavailable: $it)")
            return
        }
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(8192)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        synchronized(text) { if (text.length < LOGCAT_MAX_CHARS) text.append(buf, 0, n) }
                    }
                }
            }
        }, "crash-logcat").apply { isDaemon = true }
        reader.start()
        runCatching { process.waitFor(LOGCAT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        runCatching { reader.join(500) }
        runCatching { process.destroy() }
        synchronized(text) { out.println(text) }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"
        const val LAST_CRASH_FILE = "last_crash.txt"

        private const val LOGCAT_LINES = 3000
        private const val LOGCAT_TIMEOUT_MS = 2_000L
        private const val LOGCAT_MAX_CHARS = 512 * 1024

        // no-backup storage on purpose: the report can carry stream urls or tokens that found their
        // way into the log ring, so it must not ride to google drive with auto backup or into a
        // transfer/cloud-backup zip. The settings share row is the only way out.
        fun crashDir(context: Context): File = File(context.noBackupFilesDir, "crash")

        fun shareLatestCrash(context: Context) {
            val report = CrashLog.latestReport(crashDir(context))
            if (report == null) {
                Toast.makeText(context, R.string.crash_no_log, Toast.LENGTH_SHORT).show()
                return
            }
            runCatching {
                // the crash folder is outside the FileProvider's roots, so the share sheet gets a copy
                // in cache; the original stays in no-backup storage
                val folder = File(context.cacheDir, "crash-share").apply { mkdirs() }
                val copy = File(folder, report.name)
                report.copyTo(copy, overwrite = true)
                val uri = FileProvider.getUriForFile(context, context.packageName + ".FileProvider", copy)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_subject))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.crash_share_title)))
            }.onFailure {
                Timber.e(it, "Could not share the crash report")
            }
        }

        fun install(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Timber.d("CrashHandler installed")
        }
    }
}

/** feeds Timber into [CrashLog]'s ring buffer in every build; never throws */
class CrashLogTree : timber.log.Timber.Tree() {
    // release had no Timber tree at all, so a log call cost nothing; recording every debug line from
    // the player threads would put a format and a lock on each one. Info and up is what a report needs.
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        com.mochyy.mochymusic.BuildConfig.DEBUG || priority >= android.util.Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val level = when (priority) {
                android.util.Log.VERBOSE, android.util.Log.DEBUG -> 'D'
                android.util.Log.INFO -> 'I'
                android.util.Log.WARN -> 'W'
                else -> 'E'
            }
            CrashLog.record(level, tag ?: "Mochy", message, t)
        } catch (_: Throwable) {
        }
    }
}
