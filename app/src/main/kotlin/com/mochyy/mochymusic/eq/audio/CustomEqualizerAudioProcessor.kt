package com.mochyy.mochymusic.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.mochyy.mochymusic.eq.data.ParametricEQ
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** the equalizer's media3 half: 16-bit pcm to doubles and back, the filters and limiter themselves are shared EqEngine */
@UnstableApi
@Suppress("DEPRECATION")
class CustomEqualizerAudioProcessor : AudioProcessor {

    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var sampleRate = 0
    private var channels = 0

    private val engine = EqEngine()

    private var work = DoubleArray(0)
    // the pcm on its way in and out, moved in one copy: a getShort or putShort per sample was a native call each
    private var pcm = ShortArray(0)

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false
    private var tailPending = false
    private val tapId = System.identityHashCode(this)

    fun applyProfile(parametricEQ: ParametricEQ) = engine.applyProfile(parametricEQ)

    fun disable() = engine.disable()

    fun isEnabled(): Boolean = engine.isEnabled()

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // anything but 16-bit PCM (an encoded passthrough stream) steps out of the chain untouched
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..8) {
            pendingFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = pendingFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) {
            if (tailPending) drainTail()
            return
        }
        if (channels == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        engine.syncDesign()
        // with no eq to apply the chain is only the limiter's delay, so the pcm is copied through it as it is
        if (engine.isPassThrough && !AudioTap.wanted) {
            val frames = bytes / (2 * channels)
            val count = frames * channels
            if (pcm.size < count) pcm = ShortArray(count)
            inputBuffer.asShortBuffer().get(pcm, 0, count)
            inputBuffer.position(inputBuffer.position() + count * 2)
            val delayed = engine.delayPcm(pcm, count)
            val output = replaceOutputBuffer(bytes)
            output.asShortBuffer().put(delayed, 0, count)
            output.position(count * 2)
            output.flip()
            return
        }
        val frames = bytes / (2 * channels)
        val count = frames * channels
        ensureWork(count)
        val scale = 1.0 / 32768.0
        inputBuffer.asShortBuffer().get(pcm, 0, count)
        inputBuffer.position(inputBuffer.position() + count * 2)
        for (i in 0 until count) work[i] = pcm[i] * scale
        render(frames, replaceOutputBuffer(bytes))
    }

    /** filters, limits and writes out the frames waiting in work */
    private fun render(frames: Int, out: ByteBuffer) {
        val count = frames * channels
        engine.process(work, frames)

        if (AudioTap.wanted) AudioTap.write(tapId, work, frames, channels, sampleRate)

        for (i in 0 until count) pcm[i] = toPcm(work[i])
        out.asShortBuffer().put(pcm, 0, count)
        out.position(out.position() + count * 2)
        out.flip()
    }

    /** the song's last few milliseconds are still inside the limiter when it ends: let them out */
    private fun drainTail() {
        tailPending = false
        val frames = engine.latencyFrames
        val count = frames * channels
        ensureWork(count)
        engine.drainTail(work, frames)
        if (AudioTap.wanted) AudioTap.write(tapId, work, frames, channels, sampleRate)
        for (i in 0 until count) pcm[i] = toPcm(work[i])
        val out = replaceOutputBuffer(count * 2)
        out.asShortBuffer().put(pcm, 0, count)
        out.position(out.position() + count * 2)
        out.flip()
    }

    private fun ensureWork(count: Int) {
        if (work.size >= count) return
        work = DoubleArray(count)
        pcm = ShortArray(count)
    }

    /** one buffer, kept and reused: the chain reads each output fully before queueing more */
    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    override fun queueEndOfStream() {
        // media3 calls this again on every pass while it drains between two songs; re-arming the tail each time let the limiter's silent milliseconds out forever, so the next song never started and the old one ran on silent until a seek
        if (inputEnded) return
        inputEnded = true
        // written on the next (empty) queueInput, once the chain has read what's already out
        tailPending = channels > 0
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = inputEnded && !tailPending && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        tailPending = false
        val format = pendingFormat
        if (format != AudioProcessor.AudioFormat.NOT_SET &&
            (format.sampleRate != sampleRate || format.channelCount != channels)
        ) {
            sampleRate = format.sampleRate
            channels = format.channelCount
            engine.configure(sampleRate, channels)
        }
        // after a seek the filters start clean, and the first design lands without a fade
        engine.flush()
    }

    override fun reset() {
        flush()
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        channels = 0
        engine.reset()
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
