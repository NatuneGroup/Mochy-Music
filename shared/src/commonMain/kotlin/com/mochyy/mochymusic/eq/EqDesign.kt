package com.mochyy.mochymusic.eq

import com.mochyy.mochymusic.eq.audio.Biquad
import com.mochyy.mochymusic.eq.audio.PeakLimiter
import com.mochyy.mochymusic.eq.audio.ResponseCurve
import com.mochyy.mochymusic.eq.data.ParametricEQ
import com.mochyy.mochymusic.eq.data.ParametricEQBand
import kotlin.math.max

/**
 * Turns Audio Lab's saved settings into the curve the processors play. It is pure maths over the
 * settings, so it lives in shared code: the Android store calls it before handing the design to
 * the media3 equalizer, and a desktop or iOS player can work out the same design.
 */
object EqDesign {

    private val Frequencies = ResponseCurve.logSpaced(160)
    private val BoostRoom = DoubleArray(Frequencies.size) { PeakLimiter.boostRoomDb(Frequencies[it]) }

    /** the db the auto preamp takes off for settings (0 or less): only the boost the limiter can't catch, taking off all of it left a bass boost no louder than no eq */
    fun autoHeadroom(settings: EqSettings): Double {
        val response = ResponseCurve(Frequencies).response(audibleBands(settings))
        var over = 0.0
        for (i in response.indices) over = max(over, response[i] - BoostRoom[i])
        return -over
    }

    /** what the processors play for settings; null is a straight wire */
    fun design(settings: EqSettings): ParametricEQ? {
        if (!settings.enabled) return null
        val bands = audibleBands(settings)
        val correctionOn = settings.correctionEnabled && settings.correction != null
        val headroom = when {
            settings.autoPreamp -> autoHeadroom(settings)
            correctionOn -> settings.correction!!.preamp
            else -> 0.0
        }
        val preamp = settings.preamp + headroom
        if (bands.isEmpty() && preamp == 0.0) return null
        return ParametricEQ(preamp = preamp, bands = bands)
    }

    fun audibleBands(settings: EqSettings): List<ParametricEQBand> {
        val own = settings.nodes.filter(Biquad::isAudible)
        val correction = if (settings.correctionEnabled) settings.correction?.filters.orEmpty() else emptyList()
        return own + correction.filter(Biquad::isAudible)
    }
}
