package com.mochyy.mochymusic.crossmix

/** downmixes pcm 16 to mono and resamples it to the analyser's rate, carrying phase between calls */
class MonoResampler {
    private val out = FloatArray(8_192)
    private var count = 0
    private var position = 0.0
    private var previous = 0f

    fun push(input: ShortArray, sampleCount: Int, channels: Int, inputRate: Int, sink: (FloatArray, Int) -> Unit) {
        val step = inputRate.toDouble() / StreamingTrackAnalyzer.SampleRate
        val frames = sampleCount / channels
        var read = 0
        for (frame in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += input[read++] / 32_768f
            val current = sum / channels
            // emit every output sample that falls between the previous input sample and this one
            while (position <= 1.0) {
                out[count++] = previous + (current - previous) * position.toFloat()
                if (count == out.size) { sink(out, count); count = 0 }
                position += step
            }
            position -= 1.0
            previous = current
        }
        if (count > 0) { sink(out, count); count = 0 }
    }
}
