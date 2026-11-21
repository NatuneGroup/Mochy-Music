// DesktopAudioEngine.kt
// Decodes one stream with FFmpeg (through JavaCV, org.bytedeco) and plays the PCM on Java Sound, the
// approach Metrolist's desktop player takes (github.com/MetrolistGroup/Metrolist, GPL-3.0), cut down to
// one track at a time: open, play, pause, seek, close.

package com.mochyy.mochymusic.shared.desktop.playback

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** What the decoder found in the stream, once it is open. */
data class OpenedStream(
    val codec: String,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
)

/**
 * One open stream. [open] blocks while FFmpeg probes it, so call it off the UI thread. The decode loop runs
 * on its own daemon thread; every public method is safe to call from any thread.
 */
class DesktopAudioEngine(
    private val onEnded: () -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private val lock = java.lang.Object()
    private var grabber: FFmpegFrameGrabber? = null
    private var line: SourceDataLine? = null
    private var thread: Thread? = null

    @Volatile private var paused = false
    @Volatile private var closed = false
    @Volatile private var pendingSeekMs: Long = -1L

    // the played position is the line's frame count since the last seek, plus where that seek landed
    @Volatile private var seekBaseMs = 0L
    @Volatile private var seekBaseFrames = 0L
    private var sampleRate = 0

    fun open(url: String, headers: Map<String, String>): OpenedStream {
        FFmpegLogCallback.setLevel(avutil.AV_LOG_ERROR)
        val g = FFmpegFrameGrabber(url).apply {
            // googlevideo drops long-lived connections; FFmpeg reconnects and resumes where it was
            setOption("reconnect", "1")
            setOption("reconnect_streamed", "1")
            setOption("reconnect_delay_max", "5")
            setOption("rw_timeout", "15000000")
            headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.let { setOption("user_agent", it.value) }
            headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
                .takeIf { it.isNotEmpty() }
                ?.entries?.joinToString(separator = "\r\n", postfix = "\r\n") { "${it.key}: ${it.value}" }
                ?.let { setOption("headers", it) }
            sampleFormat = avutil.AV_SAMPLE_FMT_S16
        }
        try {
            g.start()
        } catch (error: Throwable) {
            runCatching { g.release() }
            throw error
        }
        val rate = g.sampleRate.takeIf { it > 0 } ?: 48_000
        val channels = g.audioChannels.coerceIn(1, 2)
        val format = AudioFormat(rate.toFloat(), 16, channels, true, false)
        val l = AudioSystem.getSourceDataLine(format)
        // about 250 ms of buffer: enough to ride out a slow frame, short enough that pause feels immediate
        l.open(format, rate * channels * 2 / 4)
        synchronized(lock) {
            grabber = g
            line = l
            sampleRate = rate
        }
        val opened = OpenedStream(
            codec = (g.audioCodecName ?: "").uppercase().ifBlank { "AUDIO" },
            sampleRate = rate,
            channels = channels,
            durationMs = (g.lengthInTime / 1000L).coerceAtLeast(0L),
        )
        thread = Thread(::decodeLoop, "mochy-desktop-audio").apply {
            isDaemon = true
            start()
        }
        return opened
    }

    fun pause() {
        paused = true
        line?.stop()
    }

    fun resume() {
        paused = false
        line?.start()
        synchronized(lock) { lock.notifyAll() }
    }

    fun seekTo(positionMs: Long) {
        pendingSeekMs = positionMs.coerceAtLeast(0L)
        synchronized(lock) { lock.notifyAll() }
    }

    val positionMs: Long
        get() {
            val l = line ?: return 0L
            val rate = sampleRate.takeIf { it > 0 } ?: return 0L
            val frames = (l.longFramePosition - seekBaseFrames).coerceAtLeast(0L)
            return seekBaseMs + frames * 1000L / rate
        }

    fun close() {
        closed = true
        synchronized(lock) { lock.notifyAll() }
        thread?.join(2_000)
        synchronized(lock) {
            line?.let { runCatching { it.stop(); it.flush(); it.close() } }
            grabber?.let { runCatching { it.stop(); it.release() } }
            line = null
            grabber = null
        }
    }

    private fun decodeLoop() {
        val g = grabber ?: return
        val l = line ?: return
        try {
            l.start()
            while (!closed) {
                val seek = pendingSeekMs
                if (seek >= 0) {
                    pendingSeekMs = -1L
                    l.flush()
                    g.setTimestamp(seek * 1000L, true)
                    seekBaseFrames = l.longFramePosition
                    seekBaseMs = seek
                }
                if (paused) {
                    synchronized(lock) { if (paused && !closed && pendingSeekMs < 0) lock.wait(200) }
                    continue
                }
                val frame = g.grabSamples() ?: break
                val samples = frame.samples?.firstOrNull() ?: continue
                val bytes = pcmBytes(samples) ?: continue
                l.write(bytes, 0, bytes.size)
            }
            if (!closed) {
                l.drain()
                onEnded()
            }
        } catch (error: Throwable) {
            if (!closed) onError(error)
        }
    }

    private fun pcmBytes(buffer: java.nio.Buffer): ByteArray? = when (buffer) {
        is ShortBuffer -> ByteArray(buffer.remaining() * 2).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buffer)
        }
        is ByteBuffer -> ByteArray(buffer.remaining()).also { buffer.get(it) }
        else -> null
    }
}
