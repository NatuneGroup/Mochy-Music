package com.mochyy.mochymusic.eq.audio

import com.mochyy.mochymusic.eq.data.ParametricEQ
import kotlin.concurrent.Volatile
import kotlin.math.pow

/**
 * The equalizer's audio: a cascade of biquads plus the preamp, a new design crossfaded in so
 * nothing resets or re-seeks, and the peak limiter, which always runs since toggling its delay
 * would make the music skip. It works on doubles in place; the Android media3 processor only
 * turns 16-bit pcm into these doubles and back.
 */
class EqEngine {

    private var sampleRate = 0
    private var channels = 0

    // written from the thread that changes the eq, read on the audio thread
    @Volatile private var design: ParametricEQ? = null
    @Volatile private var designVersion = 0
    private var builtVersion = -1

    private var bank: FilterBank? = null
    private var fadingFrom: FilterBank? = null
    private var fading = false
    private var fadePos = 0
    private var fadeLength = 0
    private var snapNextDesign = true

    private var fadeWork = DoubleArray(0)
    private val limiter = PeakLimiter()

    /** how many frames the limiter holds; the end of a song is still inside it */
    val latencyFrames: Int get() = limiter.latencyFrames

    /** with no eq and nothing fading, the chain is only the limiter's delay */
    val isPassThrough: Boolean get() = bank == null && !fading && limiter.isResting

    fun applyProfile(parametricEQ: ParametricEQ) {
        design = parametricEQ
        designVersion++
    }

    fun disable() {
        design = null
        designVersion++
    }

    fun isEnabled(): Boolean = design != null

    /** a new format: filters, fade and limiter start over for it */
    fun configure(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels = channels
        bank = null
        builtVersion = -1
        fading = false
        fadingFrom = null
        limiter.configure(sampleRate, channels)
        snapNextDesign = true
    }

    /** after a seek: the filters start clean, and the first design lands without a fade */
    fun flush() {
        bank?.reset()
        limiter.reset()
        fading = false
        fadingFrom = null
        if (bank == null) snapNextDesign = true
    }

    fun reset() {
        flush()
        sampleRate = 0
        channels = 0
        bank = null
        builtVersion = -1
        snapNextDesign = true
    }

    /** filters and limits the first frames frames of buffer in place */
    fun process(buffer: DoubleArray, frames: Int) {
        if (channels == 0) return
        val count = frames * channels
        syncDesign()
        if (fading) {
            if (fadeWork.size < count) fadeWork = DoubleArray(count)
            buffer.copyInto(fadeWork, 0, 0, count)
            fadingFrom?.process(fadeWork, frames)
        }
        bank?.process(buffer, frames)
        if (fading) mixFade(buffer, frames)
        limiter.process(buffer, frames)
    }

    /** the song's last few milliseconds are still inside the limiter when it ends: let them out */
    fun drainTail(buffer: DoubleArray, frames: Int) {
        if (channels == 0) return
        buffer.fill(0.0, 0, frames * channels)
        process(buffer, frames)
    }

    /** the resting case on 16-bit pcm: only the limiter's delay, copied in blocks */
    fun delayPcm(samples: ShortArray, count: Int): ShortArray = limiter.delayPcm(samples, count)

    private fun mixFade(buffer: DoubleArray, frames: Int) {
        var i = 0
        val length = fadeLength.toDouble()
        for (f in 0 until frames) {
            val g = if (fadePos >= fadeLength) 1.0 else fadePos / length
            for (c in 0 until channels) {
                buffer[i] = fadeWork[i] + (buffer[i] - fadeWork[i]) * g
                i++
            }
            if (fadePos < fadeLength) fadePos++
        }
        if (fadePos >= fadeLength) {
            fading = false
            fadingFrom = null
        }
    }

    /** picks up a new design at a buffer boundary, crossfading from what was playing */
    fun syncDesign() {
        val version = designVersion
        if (version == builtVersion) return
        builtVersion = version
        val next = design?.let { FilterBank.build(it, sampleRate, channels) }
        next?.adoptState(bank)
        if (snapNextDesign) {
            snapNextDesign = false
            bank = next
            fading = false
            fadingFrom = null
            return
        }
        if (bank == null && next == null) return
        // mid-fade, the sound being faded in becomes the one faded from: close enough to inaudible
        fadingFrom = bank
        fading = true
        fadePos = 0
        fadeLength = (sampleRate * FadeSeconds).toInt().coerceAtLeast(1)
        bank = next
    }

    /** a cascade of biquads plus the preamp, with each band's state for every channel */
    private class FilterBank(
        private val count: Int,
        private val channels: Int,
        private val coefficients: DoubleArray,
        private val gain: Double,
    ) {
        private val state = DoubleArray(count * channels * 4)

        fun process(buffer: DoubleArray, frames: Int) {
            for (k in 0 until count) {
                val o = k * Biquad.Stride
                val b0 = coefficients[o]
                val b1 = coefficients[o + 1]
                val b2 = coefficients[o + 2]
                val a1 = coefficients[o + 3]
                val a2 = coefficients[o + 4]
                for (c in 0 until channels) {
                    val s = (k * channels + c) * 4
                    var x1 = state[s]
                    var x2 = state[s + 1]
                    var y1 = state[s + 2]
                    var y2 = state[s + 3]
                    var i = c
                    for (f in 0 until frames) {
                        val x = buffer[i]
                        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                        x2 = x1
                        x1 = x
                        y2 = y1
                        y1 = y
                        buffer[i] = y
                        i += channels
                    }
                    state[s] = x1
                    state[s + 1] = x2
                    state[s + 2] = y1
                    state[s + 3] = y2
                }
            }
            if (gain != 1.0) {
                for (i in 0 until frames * channels) buffer[i] *= gain
            }
        }

        /** starts from the filters this replaces, band for band, so the sound carries on */
        fun adoptState(previous: FilterBank?) {
            if (previous == null || previous.channels != channels) return
            val shared = minOf(count, previous.count) * channels * 4
            previous.state.copyInto(state, 0, 0, shared)
        }

        fun reset() = state.fill(0.0)

        companion object {
            fun build(eq: ParametricEQ, sampleRate: Int, channels: Int): FilterBank? {
                if (sampleRate <= 0 || channels <= 0) return null
                val bands = eq.bands.filter { Biquad.isAudible(it) && it.frequency < sampleRate / 2.0 }
                val gain = 10.0.pow(eq.preamp / 20.0)
                if (bands.isEmpty() && gain == 1.0) return null
                val coefficients = DoubleArray(bands.size * Biquad.Stride)
                bands.forEachIndexed { index, band ->
                    Biquad.coefficients(band, sampleRate, coefficients, index * Biquad.Stride)
                }
                return FilterBank(bands.size, channels, coefficients, gain)
            }
        }
    }

    private companion object {
        const val FadeSeconds = 0.03
    }
}
