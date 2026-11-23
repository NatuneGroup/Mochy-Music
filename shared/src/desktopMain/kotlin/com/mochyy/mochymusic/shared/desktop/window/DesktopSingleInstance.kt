// DesktopSingleInstance.kt
// One Mochy per user. The first window holds a lock on a file in the app data folder; a second launch
// can't take it, so it writes a "show yourself" request into the same folder and leaves. The running
// window watches for those requests and comes to the front, the way a phone brings its one task back.

package com.mochyy.mochymusic.shared.desktop.window

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopSingleInstance(private val directory: File) {
    private val lockFile = directory.resolve("mochy.lock")
    private val requestPrefix = "show-"
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var watcher: Thread? = null
    private var showHandler: (() -> Unit)? = null
    private val released = AtomicBoolean(true)

    /**
     * True when this process is the one that owns the window. False means another Mochy is already
     * running: a request to show it has been left behind, and this process should exit quietly.
     */
    @Synchronized
    fun acquire(): Boolean {
        directory.mkdirs()
        val candidate = runCatching {
            FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
        }.getOrNull() ?: return true // no writable folder: never block the app over a nicety

        val candidateLock = runCatching { candidate.tryLock() }.getOrElse { null }
        if (candidateLock == null || !candidateLock.isValid) {
            runCatching { candidate.close() }
            requestShow()
            return false
        }
        channel = candidate
        lock = candidateLock
        released.set(false)
        startWatcher()
        return true
    }

    /** Called on the AWT thread for every "show yourself" a later launch asks for. */
    @Synchronized
    fun onShowRequest(handler: () -> Unit) {
        showHandler = handler
        consumeRequests(handler)
    }

    @Synchronized
    fun release() {
        if (!released.compareAndSet(false, true)) return
        watcher?.interrupt()
        watcher = null
        showHandler = null
        runCatching { lock?.release() }
        lock = null
        runCatching { channel?.close() }
        channel = null
        runCatching { lockFile.delete() }
    }

    // leaves a note for the process that owns the window; it names itself so two quiet launches don't race
    private fun requestShow() {
        val request = directory.resolve("$requestPrefix${System.nanoTime()}")
        runCatching { request.writeText(System.currentTimeMillis().toString()) }
    }

    private fun consumeRequests(handler: () -> Unit) {
        val requests = directory.listFiles { file -> file.name.startsWith(requestPrefix) }.orEmpty()
        for (request in requests) {
            if (!request.delete()) continue
            runCatching { handler() }
        }
    }

    private fun startWatcher() {
        if (watcher != null) return
        watcher = Thread({
            while (!released.get()) {
                try {
                    Thread.sleep(WatcherIntervalMs)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val handler = synchronized(this) { showHandler } ?: continue
                val requests = directory.listFiles { file -> file.name.startsWith(requestPrefix) }.orEmpty()
                if (requests.isEmpty()) continue
                for (request in requests) runCatching { request.delete() }
                // the window lives on the AWT thread, so the wake-up is posted there
                java.awt.EventQueue.invokeLater { runCatching { handler() } }
            }
        }, "MochySingleInstance").apply {
            isDaemon = true
            start()
        }
    }

    private companion object {
        const val WatcherIntervalMs = 400L
    }
}

/** The app's one single-instance check, over [com.mochyy.mochymusic.shared.desktop.DesktopPaths.instance]. */
object MochySingleInstance {
    private val instance = DesktopSingleInstance(com.mochyy.mochymusic.shared.desktop.DesktopPaths.instance)

    /** true when this process opens the window; false when another Mochy was asked to come forward */
    fun acquire(): Boolean = instance.acquire()

    fun onShowRequest(handler: () -> Unit) = instance.onShowRequest(handler)

    fun release() = instance.release()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ runCatching { instance.release() } }, "MochyInstanceRelease"))
    }
}
